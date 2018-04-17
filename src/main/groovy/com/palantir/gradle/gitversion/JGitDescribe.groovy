package com.palantir.gradle.gitversion

import com.google.common.collect.Sets
import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk

class JGitDescribe implements GitDescribe {

    private static final int SHA_ABBR_LENGTH = 7

    private File directory

    JGitDescribe(File directory) {
        this.directory = directory
    }

    @Override
    String describe(String prefix) {
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
            Set<ObjectId> tagRefs = Sets.newHashSet()
            Map<String, Ref> refs = git.getRepository().getTags()
            for (Ref ref : refs.values()) {
                tagRefs.add(ref.getPeeledObjectId())
            }

            RevWalk revWalk = new RevWalk(git.getRepository())
            revWalk.setRetainBody(false)
            RevCommit commit = revWalk.parseCommit(git.getRepository().resolve(Constants.HEAD))
            while (true) {
                if (tagRefs.contains(commit.getId())) {
                    // TODO remove this
                    String exactTag = runGitCmd("describe", "--tags", "--exact-match", "--match=${prefix}*", rev)
                    if (exactTag != "") {
                        return depth == 0 ?
                                exactTag : String.format("%s-%s-g%s", exactTag, depth, abbrevHash(revs.get(0)))
                    }
                }
                if (commit.getParentCount() == 0) {
                    break
                }
                commit = commit.getParent(0)
            }

            // No tags found, so return commit hash of HEAD
            return abbrevHash(runGitCmd("rev-parse", "HEAD"))
        } catch (Throwable t) {
            return null
        }
    }

    private String abbrevHash(String s) {
        return s.substring(0, SHA_ABBR_LENGTH)
    }
}
