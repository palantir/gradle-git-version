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

import javax.inject.Inject;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceRegistry;

public abstract class GitInvoker {

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    private final Provider<GitVersionCacheServiceV2> cacheService;

    @Inject
    public GitInvoker(BuildServiceRegistry buildServiceRegistry, ObjectFactory objectFactory) {
        cacheService = buildServiceRegistry.registerIfAbsent(
                "GitVersionCacheServiceV2", GitVersionCacheServiceV2.class, spec -> {
                    spec.parameters(
                            parameters -> parameters.getGitFactory().set(objectFactory.newInstance(Git.Factory.class)));
                });
    }

    public final Provider<String> invoke(String... command) {
        return invokeWithResult(command).map(GitExecOutput::standardOutputOfSuccessfulCommand);
    }

    public final Provider<GitExecOutput> invokeWithResult(String... command) {
        return cacheService.flatMap(service -> service.invokeWithResult(getProjectLayout(), command));
    }
}
