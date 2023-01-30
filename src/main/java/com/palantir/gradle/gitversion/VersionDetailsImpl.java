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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class VersionDetailsImpl implements VersionDetails {

    private static final Logger log = LoggerFactory.getLogger(VersionDetailsImpl.class);
    private static final int VERSION_ABBR_LENGTH = 10;
    private final GitVersionArgs args;
    private volatile String maybeCachedDescription = null;

    VersionDetailsImpl(GitVersionArgs args) {
        this.args = args;
    }

    @Override
    public String getVersion() {
        if (description() == null) {
            return "unspecified";
        }

        return description() + (isClean() ? "" : ".dirty");
    }

    private boolean isClean() {
        try {
            return git.status().call().isClean();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    private String description() {

        String rawDescription = expensiveComputeRawDescription();
        String processedDescription = rawDescription.replaceFirst("^" + args.getPrefix(), "");
        return processedDescription;
    }

    private String expensiveComputeRawDescription() {
        if (isRepoEmpty()) {
            log.debug("Repository is empty");
            return null;
        }

        String nativeGitDescribe = new NativeGitDescribe(git.getRepository().getDirectory()).describe(args.getPrefix());

        return nativeGitDescribe;
    }

    private boolean isRepoEmpty() {
        // back-compat: the JGit "describe" command throws an exception in repositories with no commits, so call it
        // first to preserve this behavior in cases where this call would fail but native "git" call does not.
        try {
            git.describe().call();
            return false;
        } catch (GitAPIException | RuntimeException ignored) {
            return true;
        }
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
        ObjectId objectId = git.getRepository().findRef(Constants.HEAD).getObjectId();
        if (objectId == null) {
            return null;
        }

        return objectId.name();
    }

    @Override
    public String getBranchName() throws IOException {
        Ref ref = git.getRepository().findRef(git.getRepository().getBranch());
        if (ref == null) {
            return null;
        }

        return ref.getName().substring(Constants.R_HEADS.length());
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
