package com.palantir.gradle.gitversion

interface GitDescribe {

    String describe(String prefix)
}
