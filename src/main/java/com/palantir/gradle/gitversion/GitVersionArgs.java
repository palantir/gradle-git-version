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

import com.google.common.base.Preconditions;
import java.util.Map;
import java.util.regex.Pattern;

final class GitVersionArgs {

    private static final Pattern PREFIX_PATTERN = Pattern.compile("[/@]?([A-Za-z]+[/@-])+");

    private final String prefix;

    GitVersionArgs(String prefix) {
        Preconditions.checkNotNull(prefix, "prefix must not be null");

        Preconditions.checkState(
                prefix.isEmpty() || PREFIX_PATTERN.matcher(prefix).matches(),
                "Specified prefix `%s` does not match the allowed format regex `%s`.",
                prefix,
                PREFIX_PATTERN);

        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    // groovy closure invocation allows any number of args
    static GitVersionArgs fromGroovyClosure(Object... objects) {
        if (objects != null && objects.length > 0 && objects[0] instanceof Map) {
            return new GitVersionArgs(((Map<?, ?>) objects[0]).get("prefix").toString());
        }

        return new GitVersionArgs("");
    }
}
