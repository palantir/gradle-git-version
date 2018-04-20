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
package com.palantir.gradle.gitversion;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description);
        return match.matches() ? Integer.valueOf(match.group(2)) : null;
    }

    private boolean descriptionIsPlainTag() {
        return !Pattern.matches(".*g.?[0-9a-fA-F]{3,}", description);
    }

    public String getLastTag() {
        if (descriptionIsPlainTag()) {
            return description;
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description);
        return match.matches() ? match.group(1) : null;
    }

    public String getGitHash() {
        return gitHash;
    }

    /** @return full 40-character Git commit hash */
    public String getGitHashFull() {
        return gitHashFull;
    }

    /** @return null if the repository in detached HEAD mode */
    public String getBranchName() {
        return branchName;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VersionDetails that = (VersionDetails) o;

        if (isClean != that.isClean) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (gitHash != null ? !gitHash.equals(that.gitHash) : that.gitHash != null) return false;
        if (gitHashFull != null ? !gitHashFull.equals(that.gitHashFull) : that.gitHashFull != null) return false;
        return branchName != null ? branchName.equals(that.branchName) : that.branchName == null;
    }

    @Override
    public int hashCode() {
        int result = description != null ? description.hashCode() : 0;
        result = 31 * result + (gitHash != null ? gitHash.hashCode() : 0);
        result = 31 * result + (gitHashFull != null ? gitHashFull.hashCode() : 0);
        result = 31 * result + (branchName != null ? branchName.hashCode() : 0);
        result = 31 * result + (isClean ? 1 : 0);
        return result;
    }
}
