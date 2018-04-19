package com.palantir.gradle.gitversion

import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref

class GitUtils {

    static final int SHA_ABBR_LENGTH = 7

    static String abbrevHash(String s) {
        return s.substring(0, SHA_ABBR_LENGTH)
    }

    static boolean isRepoEmpty(Git git) {
        // back-compat: the JGit "describe" command throws an exception in repositories with no commits, so call it
        // first to preserve this behavior in cases where this call would fail but native "git" call does not.
        try {
            new DescribeCommand(git.getRepository()).call()
            return true
        } catch (Exception ignored) {
            return false
        }
    }

    // getPeeledObjectId returns:
    // "if this ref is an annotated tag the id of the commit (or tree or blob) that the annotated tag refers to;
    // null if this ref does not refer to an annotated tag."
    // We use this to check if tag is annotated.
    static boolean isAnnotatedTag(Ref ref) {
        ObjectId peeledObjectId = ref.getPeeledObjectId()
        return peeledObjectId != null
    }
}
