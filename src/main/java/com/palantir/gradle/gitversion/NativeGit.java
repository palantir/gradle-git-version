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

interface NativeGit {

    /**
     * Mimics behaviour of 'git describe --tags --always --first-parent --match=${prefix}*'
     * Method can assume repo is not empty but should never throw.
     */
    String describe(String prefix);

    /**
     * Mimics behavior of 'git status --porcelain'.
     * @return true if no differences exist between the working-tree, the index, and the current HEAD, false if differences do exist
     */
    Boolean isClean();

    /**
     * Mimics behavior of 'git branch --show-current'.
     * @return the current branch.
     */
    String getCurrentBranch();

    String getCurrentHeadFullHash();
}
