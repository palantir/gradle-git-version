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

package com.palantir.gradle

import nebula.test.IntegrationTestKitSpec

class ConfigurationCacheTest extends IntegrationTestKitSpec {

    def setup() {
        definePluginOutsideOfPluginBlock = true
        keepFiles = true
    }

    def "classes task runs with configuration cache when using gitVersion()"() {
        given:
        setupBuildWithGitVersion("""
            apply plugin: 'com.palantir.git-version'
            apply plugin: 'java-library'

            repositories {
                mavenCentral()
                mavenLocal()
            }

            version gitVersion()
        """)

        expect:
        runTasksWithConfigurationCache('classes')
    }

    def "classes task runs with configuration cache when using versionDetails()"() {
        given:
        setupBuildWithGitVersion("""
            apply plugin: 'com.palantir.git-version'
            apply plugin: 'java-library'

            repositories {
                mavenCentral()
                mavenLocal()
            }

            versionDetails versionDetails()
        """)

        expect:
        runTasksWithConfigurationCache('classes')
    }

    /**
     * Sets up the build.gradle file with the given content.
     */
    private void setupBuildWithGitVersion(String buildFileContent) {
        // language=Gradle
        buildFile << buildFileContent.stripIndent(true)
    }

    /**
     * Runs the specified tasks twice with configuration cache and verifies cache behavior.
     * Returns true if the configuration cache was properly used on the second run.
     */
    private boolean runTasksWithConfigurationCache(String... tasks) {
        def firstRun = createRunner(tasks + ['--configuration-cache'] as String[]).build()
        assert firstRun.output.contains('Configuration cache entry stored.'),
                "Expected first run to store configuration cache, but output was: ${firstRun.output}"

        def secondRun = createRunner(tasks + ['--configuration-cache'] as String[]).build()
        assert secondRun.output.contains('Configuration cache entry reused.'),
                "Expected second run to reuse configuration cache, but output was: ${secondRun.output}"

        return true
    }
}
