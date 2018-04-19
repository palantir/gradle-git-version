package com.palantir.gradle.gitversion

import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git

class GitUtils {

    static final int SHA_ABBR_LENGTH = 7

    static String abbrevHash(String s) {
        return s.substring(0, SHA_ABBR_LENGTH)
    }

    static boolean isBackCompatible(Git git) {
        // back-compat: the JGit "describe" command throws an exception in repositories with no commits, so call it
        // first to preserve this behavior in cases where this call would fail but native "git" call does not.
        try {
            new DescribeCommand(git.getRepository()).call()
            return true
        } catch (Throwable ignored) {
            return false
        }
    }
}
