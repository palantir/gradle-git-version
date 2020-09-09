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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

final class TimingVersionDetails {
    private TimingVersionDetails() {}

    public static VersionDetails wrap(Timer timer, VersionDetails versionDetails) {
        return (VersionDetails) Proxy.newProxyInstance(
                VersionDetails.class.getClassLoader(), new Class[] {VersionDetails.class}, (_proxy, method, args) -> {
                    return timer.record(method.getName(), () -> {
                        try {
                            return method.invoke(versionDetails, args);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException("Failed in invoke method", e);
                        }
                    });
                });
    }
}
