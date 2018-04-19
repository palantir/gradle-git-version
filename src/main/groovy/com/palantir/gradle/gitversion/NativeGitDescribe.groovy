package com.palantir.gradle.gitversion

import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.collect.Sets
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Mimics git describe by using rev-list to support versions of git < 1.8.4
 */
class NativeGitDescribe implements GitDescribe {
    private static final Logger log = LoggerFactory.getLogger(NativeGitDescribe.class)

    private static final Splitter LINE_SPLITTER = Splitter.on(System.getProperty("line.separator")).omitEmptyStrings()
    private static final Splitter WORD_SPLITTER = Splitter.on(" ").omitEmptyStrings()

    private File directory

    NativeGitDescribe(File directory) {
        this.directory = directory
    }

    @Override
    String describe(String prefix) {
        if (!gitCommandExists()) {
            return null
        }

        def runGitCmd = { String... commands ->
            return GitCli.runGitCommand(directory, commands)
        }

        Git git = Git.wrap(new FileRepository(GitCli.getRootGitDir(directory)))
        if (!GitUtils.isRepoEmpty(git)) {
            log.debug("Repository is empty")
            return null
        }

        try {
            // Get SHAs of all tags, we only need to search for these later on
            Set<String> tagRefs = Sets.newHashSet()
            for (String tag : LINE_SPLITTER.splitToList(runGitCmd("show-ref", "--tags", "-d"))) {
                List<String> parts = WORD_SPLITTER.splitToList(tag)
                Preconditions.checkArgument(parts.size() == 2, "Could not parse output of `git show-ref`: %s", parts)
                tagRefs.add(parts.get(0))
            }

            List<String> revs = LINE_SPLITTER.splitToList(runGitCmd("rev-list", "--first-parent", "HEAD"))
            for (int depth = 0; depth < revs.size(); depth++) {
                String rev = revs.get(depth)
                if (tagRefs.contains(rev)) {
                    String exactTag = runGitCmd("describe", "--tags", "--exact-match", "--match=${prefix}*", rev)
                    if (exactTag != "") {
                        return depth == 0 ?
                                exactTag : String.format("%s-%s-g%s", exactTag, depth, GitUtils.abbrevHash(revs.get(0)))
                    }
                }
            }

            // No tags found, so return commit hash of HEAD
            return GitUtils.abbrevHash(runGitCmd("rev-parse", "HEAD"))
        } catch (Exception e) {
            log.debug("Native git describe failed: {}", e)
            return null
        }
    }

    private boolean gitCommandExists() {
        try {
            // verify that "git" command exists (throws exception if it does not)
            GitCli.verifyGitCommandExists()
            return true
        } catch (Exception e) {
            log.debug("Native git command not found: {}", e)
            return false
        }
    }
}
