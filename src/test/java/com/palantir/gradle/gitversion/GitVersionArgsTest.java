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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class GitVersionArgsTest {
    @Test
    public void allowed_prefixes() {
        assertThatCode(() -> new GitVersionArgs("@Product@")).doesNotThrowAnyException();
        assertThatCode(() -> new GitVersionArgs("abc@")).doesNotThrowAnyException();
        assertThatCode(() -> new GitVersionArgs("abc@test@")).doesNotThrowAnyException();
        assertThatCode(() -> new GitVersionArgs("Abc-aBc-abC@")).doesNotThrowAnyException();
        assertThatCode(() -> new GitVersionArgs("foo-bar@")).doesNotThrowAnyException();
        assertThatCode(() -> new GitVersionArgs("foo-bar/")).doesNotThrowAnyException();
        assertThatCode(() -> new GitVersionArgs("foo-bar-")).doesNotThrowAnyException();
        assertThatCode(() -> new GitVersionArgs("foo/bar@")).doesNotThrowAnyException();
        assertThatCode(() -> new GitVersionArgs("Foo/Bar@")).doesNotThrowAnyException();
    }

    @Test
    public void require_dash_or_at_symbol_at_prefix_end() {
        assertThatThrownBy(() -> new GitVersionArgs("v")).isInstanceOf(IllegalStateException.class);
    }
}
