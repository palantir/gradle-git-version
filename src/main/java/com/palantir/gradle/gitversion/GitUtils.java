package com.palantir.gradle.gitversion;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

class GitUtils {

    static final int SHA_ABBR_LENGTH = 7;

    private GitUtils() {}

    static String abbrevHash(String s) {
        return s.substring(0, SHA_ABBR_LENGTH);
    }

    // getPeeledObjectId returns:
    // "if this ref is an annotated tag the id of the commit (or tree or blob) that the annotated tag refers to;
    // null if this ref does not refer to an annotated tag."
    // We use this to check if tag is annotated.
    static boolean isAnnotatedTag(Ref ref) {
        ObjectId peeledObjectId = ref.getPeeledObjectId();
        return peeledObjectId != null;
    }
}
