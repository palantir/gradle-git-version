/*
 * Copyright 2015 Palantir Technologies
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

import groovy.transform.Memoized
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.gradle.api.Plugin
import org.gradle.api.Project

class GitVersionPlugin implements Plugin<Project> {

    private static final int VERSION_ABBR_LENGTH = 10
    private static final String PREFIX_REGEX = "[/@]?([A-Za-z]+[/@-])+"

    void apply(Project project) {
        project.ext.gitVersion = {
            args = [:] ->
                return versionDetails(project, args as GitVersionArgs).version
        }

        project.ext.versionDetails = {
            args = [:] ->
                return versionDetails(project, args as GitVersionArgs)
        }

        project.tasks.create('printVersion') {
            group = 'Versioning'
            description = 'Prints the project\'s configured version to standard out'
            doLast {
                println project.version
            }
        }
    }

    static void verifyPrefix(String prefix) {
        assert prefix != null && (prefix == "" || prefix.matches(PREFIX_REGEX)),
                "Specified prefix `${prefix}` does not match the allowed format regex `${PREFIX_REGEX}`."
    }

    private static String stripPrefix(String description, String prefix) {
        return !description ? description : description.replaceFirst("^${prefix}", "")
    }

    @Memoized
    private VersionDetails versionDetails(Project project, GitVersionArgs args) {
        verifyPrefix(args.prefix)
        String description = stripPrefix(gitDescribe(project, args.prefix), args.prefix)
        String hash = gitHash(project)
        String fullHash = gitHashFull(project)
        String branchName = gitBranchName(project)
        boolean isClean = isClean(project)

        return new VersionDetails(description, hash, fullHash, branchName, isClean)
    }

    @Memoized
    private Git gitRepo(Project project) {
        File gitDir = GitCli.getRootGitDir(project.projectDir)
        return Git.wrap(new FileRepository(gitDir))
    }

    @Memoized
    private String gitDescribe(Project project, String prefix) {
        // TODO(mbakovic): Switch to jgit only implementation
        String nativeGitDescribe = new NativeGitDescribe(project.projectDir).describe(prefix)
        String jgitDescribe = new JGitDescribe(project.projectDir).describe(prefix)
        if (nativeGitDescribe == null) {
            return jgitDescribe
        } else if (jgitDescribe == null) {
            return nativeGitDescribe
        } else {
            if (nativeGitDescribe != jgitDescribe) {
                throw new IllegalStateException(String.format(
                        "Inconsistent git describe, native was %s and jgit was %s", nativeGitDescribe, jgitDescribe))
            }
            return nativeGitDescribe
        }
    }

    @Memoized
    private String gitHash(Project project) {
        String gitHashFull = gitHashFull(project)
        if (gitHashFull == null) {
            return null
        }
        return gitHashFull.substring(0, VERSION_ABBR_LENGTH)
    }

    @Memoized
    private String gitHashFull(Project project) {
        Git git = gitRepo(project)
        ObjectId objectId = git.getRepository().getRef("HEAD").getObjectId()
        if (objectId == null) {
            return null
        }
        return objectId.name()
    }

    @Memoized
    private String gitBranchName(Project project) {
        Git git = gitRepo(project)
        Ref ref = git.getRepository().getRef(git.repository.branch)
        if (ref == null) {
            return null
        }
        return ref.getName().substring(Constants.R_HEADS.length())
    }

    @Memoized
    private boolean isClean(Project project) {
        Git git = gitRepo(project)
        return git.status().call().isClean()
    }
}
