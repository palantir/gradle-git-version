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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GitVersionArgsTest {
    @Test
    public void allowed_prefixes() throws Exception {
        new GitVersionArgs().setPrefix("@Product@");
        new GitVersionArgs().setPrefix("abc@");
        new GitVersionArgs().setPrefix("abc@test@");
        new GitVersionArgs().setPrefix("Abc-aBc-abC@");
        new GitVersionArgs().setPrefix("foo-bar@");
        new GitVersionArgs().setPrefix("foo-bar/");
        new GitVersionArgs().setPrefix("foo-bar-");
        new GitVersionArgs().setPrefix("foo/bar@");
        new GitVersionArgs().setPrefix("Foo/Bar@");
    }

    @Test
    public void require_dash_or_at_symbol_at_prefix_end() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            new GitVersionArgs().setPrefix("v");
        });
    }
}
