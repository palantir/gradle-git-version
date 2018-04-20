package com.palantir.gradle.gitversion;

class GitVersionArgs {
    private String prefix = "";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
