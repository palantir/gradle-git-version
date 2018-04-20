package com.palantir.gradle.gitversion;

interface GitDescribe {

    /**
     * Mimics behaviour of 'git describe --tags --always --first-parent --match=${prefix}*'
     * Method can assume repo is not empty but should never throw.
     */
    String describe(String prefix);
}
