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

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VersionDetailsImpl implements VersionDetails {

    private static final Logger log = LoggerFactory.getLogger(VersionDetailsImpl.class);
    static final int VERSION_ABBR_LENGTH = 10;

    private final CommonGitOperations commonGitOperations;

    VersionDetailsImpl(CommonGitOperations commonGitOperations) {
        this.commonGitOperations = commonGitOperations;
    }

    @Override
    public String getVersion() {
        return commonGitOperations.version().get();
    }

    @Override
    public boolean getIsCleanTag() {
        return commonGitOperations.isCleanTag().get();
    }

    @Override
    public int getCommitDistance() {
        return commonGitOperations.commitDistance().get();
    }

    @Override
    public String getLastTag() {
        return commonGitOperations.lastTag().get();
    }

    @Override
    public String getGitHash() throws IOException {
        return commonGitOperations.abbreviatedGitHash().get();
    }

    @Override
    public String getGitHashFull() throws IOException {
        try {
            return commonGitOperations.fullGitHash().get();
        } catch (RuntimeException e) {
            log.error("VersionDetailsImpl::getGitHashFull failed", e);
            return null;
        }
    }

    @Override
    public String getBranchName() {
        try {
            return commonGitOperations.branchName().getOrNull();
        } catch (RuntimeException e) {
            log.error("VersionDetailsImpl::getBranchName failed", e);
            return null;
        }
    }

    @Override
    public String getOriginUrl() {
        try {
            return commonGitOperations.originUrl().getOrNull();
        } catch (RuntimeException e) {
            log.error("VersionDetailsImpl::getOriginUrl failed", e);
            return null;
        }
    }

    @Override
    public String toString() {
        try {
            return String.format(
                    "VersionDetails(%s, %s, %s, %s, %s)",
                    getVersion(), getGitHash(), getGitHashFull(), getBranchName(), getIsCleanTag());
        } catch (IOException e) {
            return "";
        }
    }
}
