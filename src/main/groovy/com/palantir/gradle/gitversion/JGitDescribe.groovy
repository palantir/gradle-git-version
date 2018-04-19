package com.palantir.gradle.gitversion

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class JGitDescribe implements GitDescribe {
    private static final Logger log = LoggerFactory.getLogger(JGitDescribe.class)

    private File directory

    JGitDescribe(File directory) {
        this.directory = directory
    }

    @Override
    String describe(String prefix) {
        Git git = Git.wrap(new FileRepository(GitCli.getRootGitDir(directory)))
        if (!GitUtils.isBackCompatible(git)) {
            log.debug("Back compatibility check failed")
            return null
        }

        ObjectId headObjectId
        try {
            headObjectId = git.getRepository().resolve(Constants.HEAD)
        } catch (Throwable ignored) {
            log.debug("HEAD not found")
            return null
        }

        try {
            RevWalk walk = new RevWalk(git.getRepository())
            RevCommit commit = walk.parseCommit(headObjectId)
            List<String> revs = new ArrayList<>()
            while (commit) {
                revs.add(commit.getName())
                try {
                    commit = commit.getParent(0)
                } catch (Throwable ignored) {
                    break
                }
            }

            // Map from commit hash to all refs pointing to it
            Map<String, List<RefWithTagName>> commitHashToTags = new HashMap<>()
            for (Map.Entry<String, Ref> entry : git.getRepository().getTags()) {
                RefWithTagName refWithTagName = new RefWithTagName(entry.getValue(), entry.getKey())
                addRefToCommitHashMap(commitHashToTags, entry.getValue().getObjectId(), refWithTagName)
                // Also add dereferenced commit hash if exists
                ObjectId peeledRef = ref.getPeeledObjectId()
                if (peeledRef) {
                    addRefToCommitHashMap(commitHashToTags, peeledRef, refWithTagName)
                }
            }

            // Map from commit hash to tag chosen using defined comparator
            Map<String, String> commitHashToTag = new HashMap<>()
            RefWithTagNameComparator comparator = new RefWithTagNameComparator(walk)
            for (Map.Entry<String, List<RefWithTagName>> entry : commitHashToTags) {
                String commitHash = entry.getKey()
                RefWithTagName refWithTagName = entry.getValue().min(comparator)
                commitHashToTag.put(commitHash, refWithTagName.getTag())
            }

            for (int depth = 0; depth < revs.size(); depth++) {
                String rev = revs.get(depth)
                if (commitHashToTag.containsKey(rev)) {
                    String exactTag = commitHashToTag.get(rev)
                    if (exactTag.startsWith(prefix)) {
                        return depth == 0 ?
                                exactTag : String.format("%s-%s-g%s", exactTag, depth, GitUtils.abbrevHash(revs.get(0)))
                    }
                }
            }

            // No tags found, so return commit hash of HEAD
            return GitUtils.abbrevHash(headObjectId.getName())
        } catch (Throwable t) {
            log.debug("JGit describe failed with {}", t)
            return null
        }
    }

    private void addRefToCommitHashMap(Map<String, List<RefWithTagName>> map, ObjectId objectId, RefWithTagName ref) {
        String commitHash = objectId.getName()
        if (map.containsKey(commitHash)) {
            map.get(commitHash).add(ref)
        } else {
            map.put(commitHash, new ArrayList<RefWithTagName>([ref]))
        }
    }
}
