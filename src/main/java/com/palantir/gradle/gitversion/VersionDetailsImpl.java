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
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class VersionDetailsImpl implements VersionDetails {

    private static final Logger log = LoggerFactory.getLogger(VersionDetailsImpl.class);
    private static final int VERSION_ABBR_LENGTH = 10;
    private final GitVersionArgs args;

    private NativeGitImpl nativeGitInvoker;

    VersionDetailsImpl(GitVersionArgs args) {
        this.args = args;
        this.nativeGitInvoker = new NativeGitImpl();
    }

    @Override
    public String getVersion() {
        if (description() == null) {
            return "unspecified";
        }

        return description() + (isClean() ? "" : ".dirty");
    }

    private boolean isClean() {
        return nativeGitInvoker.isClean();
    }

    private String description() {

        String rawDescription = expensiveComputeRawDescription();
        String processedDescription = rawDescription.replaceFirst("^" + args.getPrefix(), "");
        return processedDescription;
    }

    private String expensiveComputeRawDescription() {

        String nativeGitDescribe = nativeGitInvoker.describe(args.getPrefix());

        return nativeGitDescribe;
    }

    @Override
    public boolean getIsCleanTag() {
        return isClean() && descriptionIsPlainTag();
    }

    private boolean descriptionIsPlainTag() {
        return !Pattern.matches(".*g.?[0-9a-fA-F]{3,}", description());
    }

    @Override
    public int getCommitDistance() {
        if (descriptionIsPlainTag()) {
            return 0;
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        Preconditions.checkState(match.matches(), "Cannot get commit distance for description: '%s'", description());
        return Integer.parseInt(match.group(2));
    }

    @Override
    public String getLastTag() {
        if (descriptionIsPlainTag()) {
            return description();
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        return match.matches() ? match.group(1) : null;
    }

    @Override
    public String getGitHash() throws IOException {
        String gitHashFull = getGitHashFull();
        if (gitHashFull == null) {
            return null;
        }

        return gitHashFull.substring(0, VERSION_ABBR_LENGTH);
    }

    @Override
    public String getGitHashFull() throws IOException {
        return nativeGitInvoker.getCurrentHeadFullHash();
    }

    @Override
    public String getBranchName() throws IOException {
        return nativeGitInvoker.getCurrentBranch();
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
