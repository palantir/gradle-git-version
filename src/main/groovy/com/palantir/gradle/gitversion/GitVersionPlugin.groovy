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

import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.collect.Sets
import groovy.transform.Memoized
import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GitVersionPlugin implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(GitVersionPlugin.class);
    private static final int SHA_ABBR_LENGTH = 7
    private static final int VERSION_ABBR_LENGTH = 10
    private static final String PREFIX_REGEX = "[/@]?([A-Za-z]+[/@-])+"
    private static final Splitter LINE_SPLITTER = Splitter.on(System.getProperty("line.separator")).omitEmptyStrings()
    private static final Splitter WORD_SPLITTER = Splitter.on(" ").omitEmptyStrings()

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
        File gitDir = GitCli.getRootGitDir(project.projectDir);
        return Git.wrap(new FileRepository(gitDir))
    }

    @Memoized
    private String gitDescribe(Project project, String prefix) {
        // verify that "git" command exists (throws exception if it does not)
        GitCli.verifyGitCommandExists()

        def runGitCmd = { String... commands ->
            return GitCli.runGitCommand(project.projectDir, commands);
        }

        Git git = gitRepo(project)
        try {
            // back-compat: the JGit "describe" command throws an exception in repositories with no commits, so call it
            // first to preserve this behavior in cases where this call would fail but native "git" call does not.
            new DescribeCommand(git.getRepository()).call()

            /*
             * Mimick 'git describe --tags --always --first-parent --match=${prefix}*' by using rev-list to
             * support versions of git < 1.8.4
             */

            // Get SHAs of all tags, we only need to search for these later on
            Set<String> tagRefs = Sets.newHashSet()
            for (String tag : getLines(runGitCmd("show-ref", "--tags", "-d"))) {
                List<String> parts = WORD_SPLITTER.splitToList(tag)
                Preconditions.checkArgument(parts.size() == 2, "Could not parse output of `git show-ref`: %s", parts)
                tagRefs.add(parts.get(0))
            }

            List<String> revs = getLines(runGitCmd("rev-list", "--first-parent", "HEAD"))
            for (int depth = 0; depth < revs.size(); depth++) {
                String rev = revs.get(depth)
                if (tagRefs.contains(rev)) {
                    String exactTag = runGitCmd("describe", "--tags", "--exact-match", "--match=${prefix}*", rev)
                    if (exactTag != "") {
                        return depth == 0 ?
                                exactTag : String.format("%s-%s-g%s", exactTag, depth, abbrevHash(revs.get(0)))
                    }
                }
            }

            // No tags found, so return commit hash of HEAD
            return abbrevHash(runGitCmd("rev-parse", "HEAD"))
        } catch (Throwable t) {
            log.warn("Exception raised while trying to determine git version.", t)
            return null
        }
    }

    @Memoized
    private List<String> getLines(String s) {
        return LINE_SPLITTER.splitToList(s)
    }

    @Memoized
    private String abbrevHash(String s) {
        return s.substring(0, SHA_ABBR_LENGTH)
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
        ObjectId objectId = git.getRepository().getRef("HEAD").getObjectId();
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
        return git.status().call().isClean();
    }
}
