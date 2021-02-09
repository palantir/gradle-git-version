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

class GitVersionArgs {
    private static final String PREFIX_REGEX = "[/@]?([A-Za-z][0-9A-Za-z]*[/@-])+";

    private String prefix = "";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        Preconditions.checkNotNull(prefix, "prefix must not be null");

        Preconditions.checkState(
                prefix.matches(PREFIX_REGEX),
                "Specified prefix `%s` does not match the allowed format regex `%s`.",
                prefix,
                PREFIX_REGEX);

        this.prefix = prefix;
    }

    // groovy closure invocation allows any number of args
    @SuppressWarnings("rawtypes")
    static GitVersionArgs fromGroovyClosure(Object... objects) {
        if (objects != null && objects.length > 0 && objects[0] instanceof Map) {
            GitVersionArgs instance = new GitVersionArgs();
            instance.setPrefix(((Map) objects[0]).get("prefix").toString());
            return instance;
        }

        return new GitVersionArgs();
    }
}
