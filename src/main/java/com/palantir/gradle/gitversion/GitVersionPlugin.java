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

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;

public final class GitVersionPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        Provider<GitVersionCacheService> serviceProvider = project.getGradle()
                .getSharedServices()
                .registerIfAbsent("GitVersionCacheService", GitVersionCacheService.class, spec -> {
                    // Provide some parameters
                    spec.getMaxParallelUsages().set(1);
                });

        if (project.getRootProject() == project) {
            BuildScanPluginInterop.addBuildScanCustomValues(project, () -> {
                Timer timer = serviceProvider.get().timer();

                String timerJson = timer.toJson();

                long totalTime = timer.totalMillis();

                return ImmutableMap.of(
                        "com.palantir.git-version.timings",
                        timerJson,
                        "com.palantir.git-version.timings.total",
                        Long.toString(totalTime));
            });
        }

        // intentionally not using .getExtension() here for back-compat
        project.getExtensions().getExtraProperties().set("gitVersion", new Closure<String>(this, this) {
            public String doCall(Object args) {
                return serviceProvider.get().getGitVersion();
            }
        });

        project.getExtensions().getExtraProperties().set("versionDetails", new Closure<VersionDetails>(this, this) {
            public VersionDetails doCall(Object args) {
                return serviceProvider.get().getVersionDetails();
            }
        });

        Task printVersionTask = project.getTasks().create("printVersion");
        printVersionTask.doLast(new Action<Task>() {
            @Override
            @SuppressWarnings("BanSystemOut")
            public void execute(Task _task) {
                System.out.println(project.getVersion());
            }
        });
        printVersionTask.setGroup("Versioning");
        printVersionTask.setDescription("Prints the project's configured version to standard out");
    }
}
