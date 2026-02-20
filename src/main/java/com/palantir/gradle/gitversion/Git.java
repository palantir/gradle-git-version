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

import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables;
import java.io.File;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Nested;
import org.gradle.process.ExecOutput;

/**
 * Runs git commands in a gradle-aware manner, so gradle can do up-to-date checking for the configuration cache.
 */
@SuppressWarnings("RedundantModifier")
abstract class Git {

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Nested
    protected abstract EnvironmentVariables getEnvironmentVariables();

    public abstract static class Factory {
        @Inject
        protected abstract ObjectFactory getObjectFactory();

        Git create(File repositoryDirectory) {
            return getObjectFactory().newInstance(Git.class, repositoryDirectory);
        }
    }

    private final File repositoryDirectory;

    @Inject
    public Git(File repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    interface GitParameters {
        MapProperty<String, String> getEnvironmentVariables();

        ListProperty<String> getCommand();
    }

    public Provider<GitExecOutput> runWithResult(Action<GitParameters> configureParameters) {
        GitParameters gitParameters = getObjectFactory().newInstance(GitParameters.class);
        configureParameters.execute(gitParameters);

        ExecOutput output = getProviderFactory().exec(execSpec -> {
            execSpec.executable("git");
            execSpec.args(gitParameters.getCommand().get());
            execSpec.environment(gitParameters.getEnvironmentVariables().get());
            execSpec.environment(getGitTraceEnvironmentVariables());
            execSpec.workingDir(repositoryDirectory);
            execSpec.setIgnoreExitValue(true); // So gradle doesn't throw before we get the error
        });

        ImmutableGitExecOutput.Builder execOutputBuilder = ImmutableGitExecOutput.builder()
                .command(gitParameters.getCommand().get());
        return output.getResult()
                .zip(output.getStandardOutput().getAsText(), (result, standardOut) -> execOutputBuilder
                        .uncheckedStandardOut(standardOut)
                        .exitCode(result.getExitValue()))
                .zip(output.getStandardError().getAsText(), ImmutableGitExecOutput.Builder::standardError)
                .map(ImmutableGitExecOutput.Builder::build);
    }

    public Provider<String> run(Action<GitParameters> configureParameters) {
        return runWithResult(configureParameters).map(GitExecOutput::standardOutputOfSuccessfulCommand);
    }

    public Provider<String> run(String... command) {
        return run(parameters -> parameters.getCommand().addAll(command));
    }

    private Map<String, String> getGitTraceEnvironmentVariables() {
        return getEnvironmentVariables()
                .envVarOrFromTestingProperty("GIT_TRACE")
                .map(value -> Map.of("GIT_TRACE", value))
                .getOrElse(Map.of());
    }
}
