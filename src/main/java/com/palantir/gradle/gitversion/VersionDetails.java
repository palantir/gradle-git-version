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

public final class VersionDetails {

    private static final Logger log = LoggerFactory.getLogger(VersionDetails.class);
    private static final int VERSION_ABBR_LENGTH = 10;

    private final Git git;
    private final GitVersionArgs args;
    private volatile String maybeCachedDescription = null;

    VersionDetails(GitVersionArgs args) {
        this.args = args;
    }

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
        if (maybeCachedDescription != null) {
            return maybeCachedDescription;
        }

        String rawDescription = expensiveComputeRawDescription();
        maybeCachedDescription = rawDescription == null
                ? null
                : rawDescription.replaceFirst("^" + args.getPrefix(), "");
        return maybeCachedDescription;
    }

    private String expensiveComputeRawDescription() {
        //TODO(callumr): Check what happens when git repo is empty

        String nativeGitDescribe = new NativeGitDescribe(git.getRepository().getDirectory())
                .describe(args.getPrefix());

        //TODO(callumr): Throw an error here saying jgit has been removed and you need git >1.8.4

        return nativeGitDescribe;
    }

    public boolean getIsCleanTag() {
        return isClean() && descriptionIsPlainTag();
    }

    private boolean descriptionIsPlainTag() {
        return !Pattern.matches(".*g.?[0-9a-fA-F]{3,}", description());
    }

    public int getCommitDistance() {
        if (descriptionIsPlainTag()) {
            return 0;
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        Preconditions.checkState(match.matches(), "Cannot get commit distance for description: '%s'", description());
        return Integer.parseInt(match.group(2));
    }

    public String getLastTag() {
        if (descriptionIsPlainTag()) {
            return description();
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        return match.matches() ? match.group(1) : null;
    }

    public String getGitHash() throws IOException {
        String gitHashFull = getGitHashFull();
        if (gitHashFull == null) {
            return null;
        }

        return gitHashFull.substring(0, VERSION_ABBR_LENGTH);
    }

    public String getGitHashFull() throws IOException {
        ObjectId objectId = git.getRepository().findRef(Constants.HEAD).getObjectId();
        if (objectId == null) {
            return null;
        }

        return objectId.name();
    }

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
            return String.format("VersionDetails(%s, %s, %s, %s, %s)",
                    getVersion(),
                    getGitHash(),
                    getGitHashFull(),
                    getBranchName(),
                    getIsCleanTag()
            );
        } catch (IOException e) {
            return "";
        }
    }
}
