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
package com.palantir.gradle.gitversion;

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.gradle.api.Plugin
import org.gradle.api.Project

import com.google.common.base.Preconditions

class GitVersionPlugin implements Plugin<Project> {

    private static final String NO_VERSION = 'unspecified-version'

    void apply(Project project) {
        project.ext.gitVersion = {
            File gitDir = new File(project.rootDir, '.git')
            Preconditions.checkArgument(gitDir.exists(), 'Cannot find \'.git\' directory')

            try {
                Git git = Git.wrap(new FileRepository(gitDir))
                String version = git.describe().call() ?: NO_VERSION
                boolean isClean = git.status().call().isClean()
                return version + (isClean ? '' : '.dirty')
            } catch (Throwable t) {
                return NO_VERSION
            }
        }

        project.tasks.create('printVersion', PrintVersionTask)
    }
}

