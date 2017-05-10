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

import groovy.transform.*

import java.util.regex.Matcher

/**
 * POGO containing the tag name and commit count that make
 * up the version string.
 */
@ToString
@EqualsAndHashCode
class VersionDetails implements Serializable {

    // Gradle returns 'unspecified' when no version is set
    private static final String UNSPECIFIED_VERSION = 'unspecified'
    private static final long serialVersionUID = -7340444937169877612L;

    final String description;
    final String gitHash;
    final String branchName;
    final boolean isClean;

    public VersionDetails(String description, String gitHash, String branchName, boolean isClean) {
        this.description = description;
        this.gitHash = gitHash;
        this.branchName = branchName;
        this.isClean = isClean;
    }

    public String getVersion() {
        if (description == null) {
            return UNSPECIFIED_VERSION
        }

        return description + (isClean ? '' : '.dirty')
    }

    public boolean getIsCleanTag() {
        return isClean && getCommitDistance() == 0;
    }

    public int getCommitDistance() {
        if (!(description =~ /.*g.?[0-9a-fA-F]{3,}/)) {
            // Description has no git hash so it is just the tag name
            return 0;
        }

        Matcher match = (description =~ /(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}/)
        int commitCount = Integer.valueOf(match[0][2])
        return commitCount;
    }

    public String getLastTag() {
        if (!(description =~ /.*g.?[0-9a-fA-F]{3,}/)) {
            return description;
        }

        Matcher match = (description =~ /(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}/)
        String tagName = match[0][1]
        return tagName;
    }
}
