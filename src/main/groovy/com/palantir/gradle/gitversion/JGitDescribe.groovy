package com.palantir.gradle.gitversion

class JGitDescribe implements GitDescribe {

    private File directory

    JGitDescribe(File directory) {
        this.directory = directory
    }

    @Override
    String describe(String prefix) {
        return null
    }
}
