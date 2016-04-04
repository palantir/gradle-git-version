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

import java.util.regex.Matcher

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.gradle.api.Plugin
import org.gradle.api.Project

import com.google.common.base.Supplier
import com.google.common.base.Suppliers

class GitVersionPlugin implements Plugin<Project> {

    // Gradle returns 'unspecified' when no version is set
    private static final String UNSPECIFIED_VERSION = 'unspecified'

    private Supplier<String> gitDesc

    void apply(Project project) {
        gitDesc = Suppliers.memoize(new Supplier<String>() {
            @Override
            public String get() {
                File gitDir = getRootGitDir(project.rootDir)
                try {
                    Git git = Git.wrap(new FileRepository(gitDir))
                    String version = git.describe().call() ?: UNSPECIFIED_VERSION
                    boolean isClean = git.status().call().isClean()
                    return version + (isClean ? '' : '.dirty')
                } catch (Throwable t) {
                    return UNSPECIFIED_VERSION
                }
            }
        })

        project.ext.gitVersion = {
            return gitDesc.get()
        }

        project.ext.versionDetails = {
            String description = gitDesc.get()

            if (description.equals(UNSPECIFIED_VERSION)) {
                return null
            }

            if (!(description =~ /.*g.?[0-9a-fA-F]{3,}/)) {
                // Description has no git hash so it is just the tag name
                return new VersionDetails(description, 0)
            }

            Matcher match = (description =~ /(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}/)
            String tagName = match[0][1]
            int commitCount = Integer.valueOf(match[0][2])

            return new VersionDetails(tagName, commitCount)
        }

        project.tasks.create('printVersion') << {
            println project.version
        }
    }

    private static File getRootGitDir(currentRoot) {
        File gitDir = scanForRootGitDir(currentRoot)
        if (!gitDir.exists()) {
            throw new IllegalArgumentException('Cannot find \'.git\' directory')
        }
        return gitDir
    }

    private static File scanForRootGitDir(File currentRoot) {
        File gitDir = new File(currentRoot, '.git')

        if (gitDir.exists()) {
            return gitDir
        }

        // stop at the root directory, return non-existing File object
        if (currentRoot.parentFile == null) {
            return gitDir
        }

        // look in parent directory
        return scanForRootGitDir(currentRoot.parentFile)
    }
}
