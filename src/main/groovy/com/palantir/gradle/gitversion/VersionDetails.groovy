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

/**
 * POGO containing the tag name and commit count that make
 * up the version string.
 */
class VersionDetails implements Serializable {
    private static final long serialVersionUID = -7340444937169877612L;

    final String lastTag;
    final int commitDistance;

    public VersionDetails(String lastTag, int commitDistance) {
        this.lastTag = lastTag;
        this.commitDistance = commitDistance;
    }

    public String toString() {
      return "VersionDetails[lastTag=${lastTag}, commitDistance=${commitDistance}]";
    }
}
