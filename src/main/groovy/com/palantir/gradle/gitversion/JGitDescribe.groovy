package com.palantir.gradle.gitversion

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * JGit implementation of git describe with required flags. JGit support for describe is minimal and there is no support
 * for --first-parent behavior.
 */
class JGitDescribe implements GitDescribe {
    private static final Logger log = LoggerFactory.getLogger(JGitDescribe.class)

    private File directory

    JGitDescribe(File directory) {
        this.directory = directory
    }

    @Override
    String describe(String prefix) {
        Git git = Git.wrap(new FileRepository(GitCli.getRootGitDir(directory)))
        if (!GitUtils.isRepoEmpty(git)) {
            log.debug("Repository is empty")
            return null
        }

        RevCommit headCommit
        RefWithTagNameComparator comparator
        try {
            ObjectId headObjectId = git.getRepository().resolve(Constants.HEAD)
            RevWalk walk = new RevWalk(git.getRepository())
            headCommit = walk.parseCommit(headObjectId)
            comparator = new RefWithTagNameComparator(walk)
        } catch (Exception e) {
            log.debug("HEAD not found: {}", e)
            return null
        }

        try {
            List<String> revs = revList(headCommit)

            Map<String, RefWithTagName> commitHashToTag = mapCommitsToTags(git, comparator)

            // Walk back commit ancestors looking for tagged one
            for (int depth = 0; depth < revs.size(); depth++) {
                String rev = revs.get(depth)
                if (commitHashToTag.containsKey(rev)) {
                    String exactTag = commitHashToTag.get(rev).getTag()
                    // Mimics '--match=${prefix}*' flag in 'git describe --tags --exact-match'
                    if (exactTag.startsWith(prefix)) {
                        return depth == 0 ?
                                exactTag : String.format("%s-%s-g%s", exactTag, depth, GitUtils.abbrevHash(revs.get(0)))
                    }
                }
            }

            // No tags found, so return commit hash of HEAD
            return GitUtils.abbrevHash(headCommit.toObjectId().getName())
        } catch (Exception e) {
            log.debug("JGit describe failed with {}", e)
            return null
        }
    }

    // Mimics 'git rev-list --first-parent <commit>'
    private List<String> revList(RevCommit commit) {
        List<String> revs = new ArrayList<>()
        while (commit) {
            revs.add(commit.getName())
            try {
                // There is no way to check if this exists without failing
                commit = commit.getParent(0)
            } catch (Exception ignored) {
                break
            }
        }
        return revs
    }

    // Maps all commits returned by 'git show-ref --tags -d' to output of 'git describe --tags --exact-match <commit>'
    private Map<String, RefWithTagName> mapCommitsToTags(Git git, RefWithTagNameComparator comparator) {
        // Maps commit hash to list of all refs pointing to given commit hash.
        // All keys in this map should be same as commit hashes in 'git show-ref --tags -d'
        Map<String, RefWithTagName> commitHashToTag = new HashMap<>()
        for (Map.Entry<String, Ref> entry : git.getRepository().getTags()) {
            RefWithTagName refWithTagName = new RefWithTagName(entry.getValue(), entry.getKey())
            updateCommitHashMap(commitHashToTag, comparator, entry.getValue().getObjectId(), refWithTagName)
            // Also add dereferenced commit hash if exists
            ObjectId peeledRef = refWithTagName.getRef().getPeeledObjectId()
            if (peeledRef) {
                updateCommitHashMap(commitHashToTag, comparator, peeledRef, refWithTagName)
            }
        }
        return commitHashToTag
    }

    private void updateCommitHashMap(Map<String, RefWithTagName> map, RefWithTagNameComparator comparator,
                                       ObjectId objectId, RefWithTagName ref) {
        // Smallest ref (ordered by this comparator) from list of refs is chosen for each commit.
        // This ensures we get same behavior as in 'git describe --tags --exact-match <commit>'
        String commitHash = objectId.getName()
        if (map.containsKey(commitHash)) {
            if (comparator.compare(ref, map.get(commitHash)) < 0) {
                map.put(commitHash, ref)
            }
        } else {
            map.put(commitHash, ref)
        }
    }
}
