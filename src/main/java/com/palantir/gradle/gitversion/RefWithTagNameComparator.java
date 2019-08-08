package com.palantir.gradle.gitversion;

import java.util.Comparator;
import java.util.Date;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Mimick tags comparator used by native git when doing `git describe --tags` and commit have multiple tags.
 *
 * Properties:
 *   - Annotated tag is chosen over unannotated
 *   - Newer annotated tag is chosen over older one
 *   - Lexicographically smaller unannotated tag is chosen over greater one
 */
class RefWithTagNameComparator implements Comparator<RefWithTagName> {

    private final RevWalk walk;

    RefWithTagNameComparator(Git git) {
        this.walk = new RevWalk(git.getRepository());
    }

    @Override
    public int compare(RefWithTagName tag1, RefWithTagName tag2) {
        boolean isTag1Annotated = isAnnotatedTag(tag1.getRef());
        boolean isTag2Annotated = isAnnotatedTag(tag2.getRef());

        // One is annotated, the other isn't
        if (isTag1Annotated && !isTag2Annotated) {
            return -1;
        }
        if (!isTag1Annotated && isTag2Annotated) {
            return 1;
        }

        // Both tags are unannotated, compare names
        if (!isTag1Annotated && !isTag2Annotated) {
            return tag1.getRef().getName().compareTo(tag2.getRef().getName());
        }

        // Both tags are annotated, try to return most recent one
        Date timeTag1 = getAnnotatedTagDate(tag1.getRef());
        Date timeTag2 = getAnnotatedTagDate(tag2.getRef());
        if (timeTag1 != null && timeTag2 != null) {
            // Smaller date means greater tag
            return timeTag2.compareTo(timeTag1);
        }

        // Failed to get date, assume tags are not annotated
        return tag1.getRef().getName().compareTo(tag2.getRef().getName());
    }

    // Gets date information from annotated tag. Returns null if information isn't present.
    private Date getAnnotatedTagDate(Ref ref) {
        try {
            RevTag tag = walk.parseTag(ref.getObjectId());
            PersonIdent identity = tag.getTaggerIdent();
            return identity.getWhen();
        } catch (Exception ignored) {
            return null;
        }
    }


    // getPeeledObjectId returns:
    // "if this ref is an annotated tag the id of the commit (or tree or blob) that the annotated tag refers to;
    // null if this ref does not refer to an annotated tag."
    // We use this to check if tag is annotated.
    private static boolean isAnnotatedTag(Ref ref) {
        ObjectId peeledObjectId = ref.getPeeledObjectId();
        return peeledObjectId != null;
    }
}
