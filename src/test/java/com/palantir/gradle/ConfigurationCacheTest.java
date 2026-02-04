/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class ConfigurationCacheTest {

    @Test
    void classes_task_runs_with_configuration_cache_when_using_git_version(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.git-version").add("java-library");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
                mavenLocal()
            }

            version gitVersion()
            """);

        gradle.withArgs("classes").buildsSuccessfully();
    }

    @Test
    void classes_task_runs_with_configuration_cache_when_using_version_details(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.git-version").add("java-library");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
                mavenLocal()
            }

            def details = versionDetails()
            def hash = details.getGitHash()
            def isClean = details.getIsCleanTag()
            def branchName = details.getBranchName()
            """);

        gradle.withArgs("classes").buildsSuccessfully();
    }
}
