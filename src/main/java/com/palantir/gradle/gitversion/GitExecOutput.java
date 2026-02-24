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

import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.immutables.value.Value;

@Value.Immutable
public abstract class GitExecOutput {

    private static final Logger log = Logging.getLogger(GitExecOutput.class);

    abstract List<String> command();

    public abstract int exitCode();

    public abstract String uncheckedStandardOut();

    public abstract String standardError();

    public final String standardOutputOfSuccessfulCommand() {
        assertNormalExitValue();
        return uncheckedStandardOut();
    }

    public final void assertNormalExitValue() {
        if (exitCode() == 0) {
            return;
        }

        String errorMsg =
                """
                git command [%s] failed
                Standard Error:
                %s\
                """.formatted(command().stream().collect(Collectors.joining(" ", "git ", "")), standardError());
        log.error(errorMsg);
        throw new RuntimeException(errorMsg);
    }

    @Value.Check
    protected final GitExecOutput normaliseStreams() {
        if (uncheckedStandardOut().trim().equals(uncheckedStandardOut())
                && standardError().trim().equals(standardError())) {
            return this;
        }
        return ImmutableGitExecOutput.builder()
                .from(this)
                .uncheckedStandardOut(uncheckedStandardOut().trim())
                .standardError(standardError().trim())
                .build();
    }
}
