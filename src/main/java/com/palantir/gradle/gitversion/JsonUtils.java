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

package com.palantir.gradle.gitversion;

import java.util.Map;
import java.util.stream.Collectors;

final class JsonUtils {
    private JsonUtils() {}

    static String mapToJson(Map<String, ?> map) {
        // Manually writing the json string here rather than using a library to avoid dependencies in this incredibly
        // widely used plugin.
        String middleJson = map.entrySet().stream()
                .map(entry -> String.format(
                        "\"%s\":%s", entry.getKey(), entry.getValue().toString()))
                .collect(Collectors.joining(","));

        return "{" + middleJson + "}";
    }
}
