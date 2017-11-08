package com.palantir.gradle.gitversion

class GitVersionArgs {
    String prefix = ''
    /**
     * If we're currently on a tag (or more), exclude them from git-describe so we always get a snapshot version.
     */
    boolean forceSnapshot = false
}
