package com.palantir.gradle.gitversion

import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.collect.Sets
import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository

class NativeGitDescribe implements GitDescribe {

    private static final int SHA_ABBR_LENGTH = 7
    private static final Splitter LINE_SPLITTER = Splitter.on(System.getProperty("line.separator")).omitEmptyStrings()
    private static final Splitter WORD_SPLITTER = Splitter.on(" ").omitEmptyStrings()

    private File directory

    NativeGitDescribe(File directory) {
        this.directory = directory
    }

    @Override
    String describe(String prefix) {
        // verify that "git" command exists (throws exception if it does not)
        GitCli.verifyGitCommandExists()

        def runGitCmd = { String... commands ->
            return GitCli.runGitCommand(directory, commands)
        }

        Git git = Git.wrap(new FileRepository(GitCli.getRootGitDir(directory)))
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
            return null
        }
    }

    private List<String> getLines(String s) {
        return LINE_SPLITTER.splitToList(s)
    }

    private String abbrevHash(String s) {
        return s.substring(0, SHA_ABBR_LENGTH)
    }
}
