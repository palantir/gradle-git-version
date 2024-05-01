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

import com.palantir.gradle.gitversion.Timer.Context;
import java.io.IOException;

final class TimedVersionDetails implements VersionDetails {

    private final VersionDetails delegate;
    private final Timer timer;

    TimedVersionDetails(VersionDetails delegate, Timer timer) {
        this.delegate = delegate;
        this.timer = timer;
    }

    @Override
    public String getBranchName() throws IOException {
        try (Context context = timer.start("getBranchName")) {
            return delegate.getBranchName();
        }
    }

    @Override
    public String getGitHashFull() throws IOException {
        try (Context context = timer.start("getGitHashFull")) {
            return delegate.getGitHashFull();
        }
    }

    @Override
    public String getGitHash() throws IOException {
        try (Context context = timer.start("getGitHash")) {
            return delegate.getGitHash();
        }
    }

    @Override
    public String getLastTag() {
        try (Context context = timer.start("getLastTag")) {
            return delegate.getLastTag();
        }
    }

    @Override
    public int getCommitDistance() {
        try (Context context = timer.start("getCommitDistance")) {
            return delegate.getCommitDistance();
        }
    }

    @Override
    public boolean getIsCleanTag() {
        try (Context context = timer.start("getIsCleanTag")) {
            return delegate.getIsCleanTag();
        }
    }

    @Override
    public String getVersion() {
        try (Context context = timer.start("getVersion")) {
            return delegate.getVersion();
        }
    }
}
