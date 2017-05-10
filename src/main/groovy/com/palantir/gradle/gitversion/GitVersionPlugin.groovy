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
import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.gradle.api.Plugin
import org.gradle.api.Project

class GitVersionPlugin implements Plugin<Project> {

    private static final int VERSION_ABBR_LENGTH = 10

    @Memoized
    private File gitDir(Project project) {
        return getRootGitDir(project.rootDir)
    }

    @Memoized
    private Git gitRepo(Project project) {
        return Git.wrap(new FileRepository(gitDir(project)))
    }

    @Memoized
    private String gitDescribe(Project project) {
        // verify that "git" command exists (throws exception if it does not)
        verifyGitCommandExists()

        Git git = gitRepo(project)
        try {
            // back-compat: the JGit "describe" command throws an exception in repositories with no commits, so call it
            // first to preserve this behavior in cases where this call would fail but native "git" call does not.
            new DescribeCommand(git.getRepository()).call()

            return runGitCommand(project.rootDir, "describe", "--tags", "--always", "--first-parent")
        } catch (Throwable t) {
            return null
        }
    }

    @Memoized
    private boolean isClean(Project project) {
        Git git = gitRepo(project)
        return git.status().call().isClean();
    }

    @Memoized
    private String gitHash(Project project) {
        Git git = gitRepo(project)
        ObjectId objectId = git.getRepository().getRef("HEAD").getObjectId();
        if (objectId == null) {
            return null
        }
        return objectId.abbreviate(VERSION_ABBR_LENGTH).name()
    }

    @Memoized
    private String gitBranchName(Project project) {
        Git git = gitRepo(project)
        Ref ref = git.repository.getRef(git.repository.branch)
        if (ref == null) {
            return null
        }
        return ref.getName().substring(Constants.R_HEADS.length())
    }

    @Memoized
    private VersionDetails versionDetails(Project project) {
        String description = gitDescribe(project)
        String hash = gitHash(project)
        String branchName = gitBranchName(project)
        boolean isClean = isClean(project)

        return new VersionDetails(description, hash, branchName, isClean)
    }

    void apply(Project project) {
        project.ext.gitVersion = {
            return versionDetails(project).version
        }

        project.ext.versionDetails = {
            return versionDetails(project)
        }

        project.tasks.create('printVersion') {
            group = 'Versioning'
            description = 'Prints the project\'s configured version to standard out'
            doLast {
                println project.version
            }
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

    private static void verifyGitCommandExists() {
        Process gitVersionProcess = new ProcessBuilder("git", "version").start()
        if (gitVersionProcess.waitFor() != 0) {
            throw new IllegalStateException("error invoking git command")
        }
    }

    private static String runGitCommand(File dir, String ...commands) {
        List<String> cmdInput = new ArrayList<>()
        cmdInput.add("git")
        cmdInput.addAll(commands)
        ProcessBuilder pb = new ProcessBuilder(cmdInput)
        pb.directory(dir)
        pb.redirectErrorStream(true)

        Process process = pb.start()
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))

        StringBuilder builder = new StringBuilder()
        String line = null
        while ((line = reader.readLine()) != null) {
            builder.append(line)
            builder.append(System.getProperty("line.separator"))
        }

        int exitCode = process.waitFor()
        if (exitCode != 0) {
            return ""
        }

        return builder.toString().trim()
    }

}
