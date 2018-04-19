package com.palantir.gradle.gitversion

import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk

/**
 * Mimick tags comparator used by native git when doing `git describe --tags` and commit have multiple tags.
 *
 * Properties:
 *   - Annotated tag is chosen over unannotated
 *   - Newer annotated tag is chosen over older one
 *   - Lexicographically smaller unannotated tag is chosen over greater one
 */
class RefWithTagNameComparator implements Comparator<RefWithTagName> {

    private RevWalk walk

    RefWithTagNameComparator(RevWalk walk) {
        this.walk = walk
    }

    @Override
    int compare(RefWithTagName tag1, RefWithTagName tag2) {
        boolean isTag1Annotated = GitUtils.isAnnotatedTag(tag1.getRef())
        boolean isTag12nnotated = GitUtils.isAnnotatedTag(tag2.getRef())

        // One is annotated, the other isn't
        if (isTag1Annotated && !isTag12nnotated) {
            return -1
        }
        if (!isTag1Annotated && isTag12nnotated) {
            return 1
        }

        // Both tags are unannotated, compare names
        if (!isTag1Annotated && !isTag12nnotated) {
            return tag1.getRef().getName().compareTo(tag2.getRef().getName())
        }

        // Both tags are annotated, try to return most recent one
        Date timeTag1 = getAnnotatedTagDate(tag1.getRef())
        Date timeTag2 = getAnnotatedTagDate(tag2.getRef())
        if (timeTag1 && timeTag2) {
            // Smaller date means greater tag
            return timeTag2.compareTo(timeTag1)
        }

        // Failed to get date, assume tags are not annotated
        return tag1.getRef().getName().compareTo(tag2.getRef().getName())
    }

    // Gets date information from annotated tag. Returns null if information isn't present.
    private Date getAnnotatedTagDate(Ref ref) {
        try {
            RevTag tag = walk.parseTag(ref.getObjectId())
            PersonIdent identity = tag.getTaggerIdent()
            return identity.getWhen()
        } catch (Exception ignored) {
            return null
        }
    }
}
