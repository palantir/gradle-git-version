/*
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.gitversion

import java.util.regex.Matcher

public class VersionDetails implements Serializable {

    // Gradle returns 'unspecified' when no version is set
    private static final String UNSPECIFIED_VERSION = "unspecified";
    private static final long serialVersionUID = -7340444937169877612L;

    private final String description;
    private final String gitHash;
    private final String gitHashFull;
    private final String branchName;
    private final boolean isClean;

    public VersionDetails(String description, String gitHash, String gitHashFull, String branchName, boolean isClean) {
        this.description = description;
        this.gitHash = gitHash;
        this.gitHashFull = gitHashFull;
        this.branchName = branchName;
        this.isClean = isClean;
    }

    public String getVersion() {
        if (description == null) {
            return UNSPECIFIED_VERSION;
        }

        return description + (isClean ? "" : ".dirty");
    }

    public boolean getIsCleanTag() {
        return isClean && descriptionIsPlainTag();
    }

    public int getCommitDistance() {
        if (descriptionIsPlainTag()) {
            return 0;
        }

        Matcher match = (description =~ /(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}/)
        int commitCount = Integer.valueOf(match[0][2]);
        return commitCount;
    }

    private boolean descriptionIsPlainTag() {
        return !(description =~ /.*g.?[0-9a-fA-F]{3,}/);
    }

    public String getLastTag() {
        if (descriptionIsPlainTag()) {
            return description;
        }

        Matcher match = (description =~ /(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}/)
        String tagName = match[0][1];
        return tagName;
    }

    public String getGitHash() {
        return gitHash;
    }

    /** full 40-character Git commit hash */
    public String getGitHashFull() {
        return gitHashFull;
    }

    /** returns null if the repository in detached HEAD mode */
    public String getBranchName() {
        return branchName;
    }

    public boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        VersionDetails that = (VersionDetails) o

        if (isClean != that.isClean) return false
        if (branchName != that.branchName) return false
        if (description != that.description) return false
        if (gitHash != that.gitHash) return false
        if (gitHashFull != that.gitHashFull) return false

        return true
    }

    public int hashCode() {
        int result
        result = (description != null ? description.hashCode() : 0)
        result = 31 * result + (gitHash != null ? gitHash.hashCode() : 0)
        result = 31 * result + (gitHashFull != null ? gitHashFull.hashCode() : 0)
        result = 31 * result + (branchName != null ? branchName.hashCode() : 0)
        result = 31 * result + (isClean ? 1 : 0)
        return result
    }

    @Override
    public String toString() {
        return "VersionDetails{" +
                "description='" + description + '\'' +
                ", gitHash='" + gitHash + '\'' +
                ", gitHashFull='" + gitHashFull + '\'' +
                ", branchName='" + branchName + '\'' +
                ", isClean=" + isClean +
                '}';
    }
}
