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

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.immutables.value.Value;

final class Timer {
    private final ConcurrentMap<String, Stats> totalTimesTakeMillis = new ConcurrentHashMap<>();

    public <T> T record(String name, Supplier<T> codeToTime) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            return codeToTime.get();
        } finally {
            stopwatch.stop();
            long timeTakenMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);

            totalTimesTakeMillis.compute(name, (_ignored, previousValue) -> {
                return Optional.ofNullable(previousValue)
                        .orElseGet(Stats::empty)
                        .add(timeTakenMillis);
            });
        }
    }

    public String toJson() {
        Map<String, Long> withTotal = ImmutableMap.<String, Long>builder()
                .putAll(totalTimesTakeMillis)
                .put("total", totalMillis())
                .build();

        return JsonUtils.mapToJson(withTotal);
    }

    public long totalMillis() {
        return totalTimesTakeMillis.values().stream().mapToLong(time -> time).sum();
    }

    @Value.Immutable
    interface Stats {
        long timesCalled();

        long timeTakenMillis();

        default Stats add(long anotherTimeTakenMillis) {
            return ImmutableStats.builder()
                    .timesCalled(timesCalled() + 1)
                    .timeTakeMillis(timeTakenMillis() + anotherTimeTakenMillis)
                    .build();
        }

        static Stats empty() {
            return ImmutableStats.builder().timesCalled(0).timeTakeMillis(0).build();
        }
    }
}
