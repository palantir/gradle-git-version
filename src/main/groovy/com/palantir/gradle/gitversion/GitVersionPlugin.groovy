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
import org.gradle.api.Task

class GitVersionPlugin implements Plugin<Project> {

    private static final int VERSION_ABBR_LENGTH = 10
    private static final String PREFIX_REGEX = "[/@]?([A-Za-z]+[/@-])+"

    void apply(Project project) {
        project.ext.gitVersion = { args ->
            return versionDetails(project, GitVersionArgs.fromGroovyClosure(args)).version
        }

        project.ext.versionDetails = { args ->
            return versionDetails(project, GitVersionArgs.fromGroovyClosure(args))
        }

        Task printVersionTask = project.getTasks().create("printVersion", {
            doLast {
                println project.version
            }
        });
        printVersionTask.setGroup("Versioning")
        printVersionTask.setDescription("Prints the project's configured version to standard out");
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
        Git git = gitRepo(project)
        String description = stripPrefix(gitDescribe(project, args.prefix), args.prefix)
        String hash = gitHash(git)
        String fullHash = gitHashFull(git)
        String branchName = gitBranchName(git)
        boolean isClean = isClean(git)

        return new VersionDetails(description, hash, fullHash, branchName, isClean)
    }

    @Memoized
    private Git gitRepo(Project project) {
        File gitDir = GitCli.getRootGitDir(project.projectDir)
        return Git.wrap(new FileRepository(gitDir))
    }

    @Memoized
    private String gitDescribe(Project project, String prefix) {
        // This used to be implemented with JGit and replaced with shelling out to installed git (#46) because JGit
        // didn't support required behavior. Using installed git doesn't work in some environments or
        // with older versions of git client. We're switching back to implementation with JGit. To make sure we don't
        // make breaking change, we're keeping both implementations. Plan is to get rid of installed git implementation.
        // TODO(mbakovic): Use JGit only implementation #87

        Git git = gitRepo(project);
        String nativeGitDescribe = new NativeGitDescribe(project.projectDir, git).describe(prefix)
        String jgitDescribe = new JGitDescribe(git).describe(prefix)

        // If native failed, return JGit one
        if (nativeGitDescribe == null) {
            return jgitDescribe
        }

        // If native succeeded, make sure it's same as JGit one
        if (!nativeGitDescribe.equals(jgitDescribe)) {
            throw new IllegalStateException(String.format(
                    "Inconsistent git describe: native was %s and jgit was %s. "
                    + "Please report this on github.com/palantir/gradle-git-version",
                    nativeGitDescribe, jgitDescribe))
        }

        return jgitDescribe
    }

    @Memoized
    private String gitHash(Git git) {
        String gitHashFull = gitHashFull(git)
        if (gitHashFull == null) {
            return null
        }
        return gitHashFull.substring(0, VERSION_ABBR_LENGTH)
    }

    @Memoized
    private String gitHashFull(Git git) {
        ObjectId objectId = git.getRepository().findRef(Constants.HEAD).getObjectId()
        if (objectId == null) {
            return null
        }
        return objectId.name()
    }

    @Memoized
    private String gitBranchName(Git git) {
        Ref ref = git.getRepository().findRef(git.getRepository().getBranch())
        if (ref == null) {
            return null
        }
        return ref.getName().substring(Constants.R_HEADS.length())
    }

    @Memoized
    private boolean isClean(Git git) {
        return git.status().call().isClean()
    }
}
