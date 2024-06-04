/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Suppliers;
import java.util.function.Supplier;

final class CachingGit implements Git {

    private final Supplier<String> currentBranch;
    private final Supplier<String> currentHeadFullHash;
    private final Supplier<Boolean> isClean;
    private final Supplier<String> describe;

    CachingGit(Git delegate) {
        this.currentBranch = Suppliers.memoize(delegate::getCurrentBranch);
        this.currentHeadFullHash = Suppliers.memoize(delegate::getCurrentHeadFullHash);
        this.isClean = Suppliers.memoize(delegate::isClean);
        this.describe = Suppliers.memoize(delegate::describe);
    }

    @Override
    public String getCurrentBranch() {
        return currentBranch.get();
    }

    @Override
    public String getCurrentHeadFullHash() {
        return currentHeadFullHash.get();
    }

    @Override
    public Boolean isClean() {
        return isClean.get();
    }

    @Override
    public String describe() {
        return describe.get();
    }
}
