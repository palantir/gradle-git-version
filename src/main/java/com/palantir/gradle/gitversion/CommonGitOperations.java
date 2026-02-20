/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.gitversion;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.palantir.gradle.gitversion.CommonGitOperations.Factory.Parameters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Nested;

public abstract class CommonGitOperations {

    private static final int VERSION_ABBR_LENGTH = 10;

    public abstract static class Default extends CommonGitOperations {
        @Inject
        public Default(ObjectFactory objectFactory) {
            super(objectFactory.newInstance(Parameters.class));
        }
    }

    public abstract static class Factory {

        public interface Parameters {
            Property<String> getPrefix();
        }

        @Inject
        protected abstract ObjectFactory getObjectFactory();

        public final CommonGitOperations create(Action<Parameters> configurator) {
            Parameters parameters = getObjectFactory().newInstance(Parameters.class);
            configurator.execute(parameters);
            return getObjectFactory().newInstance(CommonGitOperations.class, parameters);
        }
    }

    private final Parameters parameters;

    @Nested
    protected abstract GitInvoker getInvoker();

    @Nested
    protected abstract GitVersionOverride getGitVersionOverride();

    @Inject
    public CommonGitOperations(Parameters parameters) {
        this.parameters = parameters;
    }

    public final Provider<String> version() {
        Provider<String> calculatedVersion = description()
                .zip(isClean(), (description, isClean) -> description + (isClean ? "" : ".dirty"))
                .orElse("unspecified");
        return getGitVersionOverride().getOverride().orElse(calculatedVersion);
    }

    public final Provider<String> originUrl() {
        return getInvoker().invoke("config", "remote.origin.url").map(Strings::emptyToNull);
    }

    public final Provider<String> branchName() {
        return getInvoker().invoke("branch", "--show-current").map(Strings::emptyToNull);
    }

    public final Provider<String> fullGitHash() {
        return getInvoker().invoke("rev-parse", "HEAD");
    }

    public final Provider<String> abbreviatedGitHash() {
        return fullGitHash().map(fullGitHash -> fullGitHash.substring(0, VERSION_ABBR_LENGTH));
    }

    public final Provider<Boolean> isCleanTag() {
        return isClean().zip(description(), (isClean, description) -> isClean && description.isPlainTag());
    }

    public final Provider<Integer> commitDistance() {
        return description().map(Description::commitDistance);
    }

    public final Provider<String> lastTag() {
        return description().map(Description::lastTag);
    }

    private Provider<Boolean> isClean() {
        return getInvoker().invoke("status", "--porcelain").map(String::isEmpty);
    }

    private Provider<Description> description() {
        Provider<String> nonEmptyPrefix = parameters.getPrefix().orElse("");
        return nonEmptyPrefix.flatMap(this::describe).map(Strings::emptyToNull).map(Description::new);
    }

    private Provider<String> describe(String prefixValue) {
        return getInvoker()
                .invokeWithResult(
                        "describe",
                        "--tags",
                        "--always",
                        "--first-parent",
                        "--abbrev=7",
                        "--match=%s*".formatted(prefixValue),
                        "HEAD")
                .map(execOutput -> {
                    if (execOutput.exitCode() == 0) {
                        return execOutput.uncheckedStandardOut();
                    }

                    if (execOutput.standardError().contains("Not a valid object name HEAD")) {
                        return null;
                    }

                    // Always fails
                    execOutput.assertNormalExitValue();
                    return null;
                })
                .map(rawDescription -> rawDescription.replaceFirst("^%s".formatted(prefixValue), ""));
    }

    private record Description(String description) {

        private static final Pattern GIT_DESCRIBE_PATTERN = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}");

        public String lastTag() {
            if (isPlainTag()) {
                return description;
            }

            Matcher match = GIT_DESCRIBE_PATTERN.matcher(description);
            return match.matches() ? match.group(1) : null;
        }

        public int commitDistance() {
            if (isPlainTag()) {
                return 0;
            }

            Matcher match = GIT_DESCRIBE_PATTERN.matcher(description);
            Preconditions.checkState(match.matches(), "Cannot get commit distance for description: '%s'", description);
            return Integer.parseInt(match.group(2));
        }

        private boolean isPlainTag() {
            return !Pattern.matches(".*g.?[0-9a-fA-F]{3,}", description);
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
