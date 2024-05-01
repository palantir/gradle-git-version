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
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionDetailsImpl implements VersionDetails {

    private static final Pattern NOT_PLAIN_TAG_PATTERN = Pattern.compile(".*g.?[0-9a-fA-F]{3,}");

    private static final int VERSION_ABBR_LENGTH = 10;

    private static final String DOT_GIT_DIR_PATH = "/.git";

    private final Git git;
    private final GitVersionArgs args;

    VersionDetailsImpl(File gitDir, GitVersionArgs args) {
        String gitDirStr = gitDir.toString();
        String projectDir = gitDirStr.substring(0, gitDirStr.length() - DOT_GIT_DIR_PATH.length());
        this.git = new CachingGit(new GitImpl(new File(projectDir), args));
        this.args = args;
    }

    @Override
    public String getVersion() {
        String description = description();
        if (description == null) {
            return "unspecified";
        }

        return description + (git.isClean() ? "" : ".dirty");
    }

    @Override
    public boolean getIsCleanTag() {
        return git.isClean() && descriptionIsPlainTag();
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
        String gitHashFull = git.getCurrentHeadFullHash();
        if (gitHashFull == null) {
            return null;
        }

        return gitHashFull.substring(0, VERSION_ABBR_LENGTH);
    }

    @Override
    public String getGitHashFull() throws IOException {
        return git.getCurrentHeadFullHash();
    }

    @Override
    public String getBranchName() throws IOException {
        return git.getCurrentBranch();
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

    private String description() {
        String describe = git.describe();
        if (describe == null) {
            return null;
        }

        if (describe.startsWith(args.getPrefix())) {
            return describe.substring(args.getPrefix().length());
        }

        return describe;
    }

    private boolean descriptionIsPlainTag() {
        return !NOT_PLAIN_TAG_PATTERN.matcher(description()).matches();
    }
}
