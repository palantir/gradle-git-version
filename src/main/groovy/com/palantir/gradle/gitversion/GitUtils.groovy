package com.palantir.gradle.gitversion

class GitUtils {

    static final int SHA_ABBR_LENGTH = 7

    static String abbrevHash(String s) {
        return s.substring(0, SHA_ABBR_LENGTH)
    }
}
