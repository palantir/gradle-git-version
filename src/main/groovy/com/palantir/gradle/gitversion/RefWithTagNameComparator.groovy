package com.palantir.gradle.gitversion

import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk

/**
 * Mimick tags comparator used by native git when doing `git describe` and commit have multiple tags.
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
        // Returns null if tag is not annotated or date is not present
        Date timeTag1 = getAnnotatedTagDate(tag1.getRef())
        Date timeTag2 = getAnnotatedTagDate(tag2.getRef())

        // Both tags are annotated
        if (timeTag1 != null && timeTag2 != null) {
            return timeTag1.compareTo(timeTag2)
        }

        // One is annotated, the other isn't
        if (timeTag1 != null && timeTag2 != null) {
            return -1
        }
        if (timeTag1 == null && timeTag2 != null) {
            return 1
        }

        // Both tags are unannotated
        return tag1.getRef().getName().compareTo(tag2.getRef().getName())
    }

    private Date getAnnotatedTagDate(Ref ref) {
        try {
            RevTag tag = walk.parseTag(ref.getObjectId())
            PersonIdent identity = tag.getTaggerIdent()
            return identity.getWhen()
        } catch (Throwable ignored) {
            return null
        }
    }
}
