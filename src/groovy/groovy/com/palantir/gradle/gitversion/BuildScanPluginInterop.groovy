/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.gitversion

import java.util.function.Supplier
import org.gradle.api.Project

class BuildScanPluginInterop {
    static void addBuildScanCustomValues(Project rootProject, Supplier<Map<String, String>> customValues) {
        // Fix #353: Detect when the root project is already evaluated, thus anything is afterEvaluate
        // and we can just execute immediately. This is due to afterEvaluate changes in Gradle 7.
        //
        // Note: We cannot use hasCompleted() in order to continue supporting gradle 5, a minor inconvenience
        if (rootProject.getState().isUnconfigured() || rootProject.getState().isConfiguring()) {
            // After evaluate because while we can detect the <5.x com.gradle.build-scan project on the root project,
            // there is no way to detect the >6.x com.gradle.enterprise settings plugins using withPlugin
            rootProject.afterEvaluate {
                applyBuildScanCustomValues(rootProject, customValues)
            }
        } else {
            applyBuildScanCustomValues(rootProject, customValues)
        }
    }

    private static void applyBuildScanCustomValues(Project rootProject, Supplier<Map<String, String>> customValues) {
        def gradleEnterpriseExtension = rootProject.extensions.findByName('gradleEnterprise')

        // In Gradle Enterprise Plugin 3.2, the root project's buildScan extension was deprecated and you now need
        // to call gradleEnterprise.buildScan
        def buildScan = Optional.ofNullable(gradleEnterpriseExtension)
                .map({ gradleEnterprise -> gradleEnterprise.buildScan })
                .orElseGet({ rootProject.extensions.findByName('buildScan') })

        if (buildScan == null) {
            return
        }

        buildScan.buildFinished {
            customValues.get().forEach({ name, value ->  rootProject.buildScan.value(name, value) })
        }
    }
}
