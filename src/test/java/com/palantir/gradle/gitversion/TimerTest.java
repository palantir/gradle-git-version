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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.palantir.gradle.gitversion.Timer.Context;
import org.junit.jupiter.api.Test;

class TimerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void generate_correct_json_with_total() throws JsonProcessingException {
        Timer timer = new Timer();
        try (Context context = timer.start("something")) {
            // Empty
        }
        try (Context context = timer.start("another")) {
            // Empty
        }

        ObjectNode objectNode = OBJECT_MAPPER.readValue(timer.toJson(), ObjectNode.class);
        long something = objectNode.get("something").asLong();
        long another = objectNode.get("another").asLong();
        long total = objectNode.get("total").asLong();

        assertThat(something).isGreaterThanOrEqualTo(0);
        assertThat(another).isGreaterThanOrEqualTo(0);
        assertThat(total).isEqualTo(something + another);
        assertThat(timer.totalMillis()).isEqualTo(total);
    }
}
