/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VersionDetailsImpl implements VersionDetails {

    private static final Logger log = LoggerFactory.getLogger(VersionDetailsImpl.class);
    private static final int VERSION_ABBR_LENGTH = 10;

    private Provider<String> description;
    private Provider<Boolean> isClean;
    private Provider<String> gitHashFull;
    private Provider<String> branchName;
    private Provider<String> originUrl;
    private EnvironmentVariables environmentVariables;

    VersionDetailsImpl(
            ProviderFactory providerFactory,
            EnvironmentVariables environmentVariables,
            File gitDir,
            GitVersionArgs gitVersionArgs) {
        String projectDir = gitDir.getParent();
        Git git = new Git(new File(projectDir), providerFactory);
        this.description = git.run(
                        "describe",
                        "--tags",
                        "--always",
                        "--first-parent",
                        "--abbrev=7",
                        "--match=" + gitVersionArgs.getPrefix() + "*",
                        "HEAD")
                .map(rawDescription -> rawDescription.replaceFirst("^" + gitVersionArgs.getPrefix(), ""));
        this.isClean = git.run("status", "--porcelain").map(String::isEmpty);
        this.gitHashFull = git.run("rev-parse", "HEAD");
        this.branchName = git.run("branch", "--show-current");
        this.originUrl = git.run("config", "remote.origin.url");
        this.environmentVariables = environmentVariables;
    }

    @Override
    public String getVersion() {
        Provider<String> envVersion = environmentVariables.envVarOrFromTestingProperty("GIT_VERSION");
        if (envVersion.isPresent() && !envVersion.get().isEmpty()) {
            return envVersion.get();
        }

        if (description() == null) {
            return "unspecified";
        }
        return description() + (isClean() ? "" : ".dirty");
    }

    private boolean isClean() {
        return isClean.get();
    }

    private String description() {
        try {
            return Strings.emptyToNull(this.description.get());
        } catch (RuntimeException e) {
            log.error("VersionDetailsImpl::getGitHashFull failed", e);
            return null;
        }
    }

    @Override
    public boolean getIsCleanTag() {
        return isClean() && descriptionIsPlainTag();
    }

    private boolean descriptionIsPlainTag() {
        return !Pattern.matches(".*g.?[0-9a-fA-F]{3,}", description());
    }

    @Override
    public int getCommitDistance() {
        if (descriptionIsPlainTag()) {
            return 0;
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        Preconditions.checkState(match.matches(), "Cannot get commit distance for description: '%s'", description());
        return Integer.parseInt(match.group(2));
    }

    @Override
    public String getLastTag() {
        if (descriptionIsPlainTag()) {
            return description();
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        return match.matches() ? match.group(1) : null;
    }

    @Override
    public String getGitHash() throws IOException {
        return getGitHashFull().substring(0, VERSION_ABBR_LENGTH);
    }

    @Override
    public String getGitHashFull() throws IOException {
        try {
            return gitHashFull.get();
        } catch (RuntimeException e) {
            log.error("VersionDetailsImpl::getGitHashFull failed", e);
            return null;
        }
    }

    @Override
    public String getBranchName() {
        try {
            return Strings.emptyToNull(this.branchName.get());
        } catch (RuntimeException e) {
            log.error("VersionDetailsImpl::getBranchName failed", e);
            return null;
        }
    }

    @Override
    public String getOriginUrl() {
        try {
            return Strings.emptyToNull(this.originUrl.get());
        } catch (RuntimeException e) {
            log.error("VersionDetailsImpl::getOriginUrl failed", e);
            return null;
        }
    }

    @Override
    public String toString() {
        try {
            return String.format(
                    "VersionDetails(%s, %s, %s, %s, %s)",
                    getVersion(), getGitHash(), getGitHashFull(), getBranchName(), getIsCleanTag());
        } catch (IOException e) {
            return "";
        }
    }
}
