package com.palantir.gradle.gitversion

interface GitDescribe {

    /**
     * Mimics behaviour of 'git describe --tags --always --first-parent --match=${prefix}*'
     * Method returns null if repository is empty.
     */
    String describe(String prefix)
}
