package com.palantir.gradle.gitversion;

import org.eclipse.jgit.lib.Ref;

class RefWithTagName {

    // JGit Ref object
    private final Ref ref;

    // Named returned when getting all tags
    private final String tag;

    RefWithTagName(Ref ref, String tag) {
        this.ref = ref;
        this.tag = tag;
    }

    Ref getRef() {
        return ref;
    }

    String getTag() {
        return tag;
    }
}
