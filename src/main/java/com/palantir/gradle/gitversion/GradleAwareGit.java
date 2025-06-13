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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs git commands in a gradle-aware manner, so gradle can do up-to-date checking for the configuration cache.
 */
class GradleAwareGit {
    public static class GitCommandFailed extends Exception {
        @SuppressWarnings("checkstyle:visibilitymodifier")
        public int exitCode;

        @SuppressWarnings("checkstyle:visibilitymodifier")
        public String errorOutput;

        GitCommandFailed(String message, int exitCode, String errorOutput) {
            super(message);
            this.exitCode = exitCode;
            this.errorOutput = errorOutput;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(GradleAwareGit.class);

    private final File repositoryDir;
    private final ProviderFactory providerFactory;

    GradleAwareGit(File repositoryDir, ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
        this.repositoryDir = repositoryDir;
        if (!gitCommandExists()) {
            throw new RuntimeException("git command not found in project.");
        }
    }

    private boolean gitCommandExists() {
        return providerFactory
                        .exec(execSpec -> {
                            execSpec.executable("git");
                            execSpec.args("version");
                            execSpec.workingDir(repositoryDir);
                        })
                        .getResult()
                        .get()
                        .getExitValue()
                == 0;
    }

    public String run(Map<String, String> envvars, String... commands) throws GitCommandFailed {
        ExecOutput output = providerFactory.exec(execSpec -> {
            execSpec.executable("git");
            execSpec.args((Object[]) commands);
            execSpec.environment(envvars);
            execSpec.workingDir(repositoryDir);
            execSpec.setIgnoreExitValue(true); // So gradle doesn't throw before we get the error
        });

        int exitValue = output.getResult().get().getExitValue();
        if (exitValue != 0) {
            String stdErr = output.getStandardError().getAsText().get();
            String errorMsg = String.format("git command failed: %s", stdErr);
            log.error(errorMsg);
            throw new GitCommandFailed(errorMsg, exitValue, stdErr);
        }

        return output.getStandardOutput().getAsText().get().trim();
    }

    public String run(String... command) throws GitCommandFailed {
        return run(new HashMap<>(), command);
    }
}
