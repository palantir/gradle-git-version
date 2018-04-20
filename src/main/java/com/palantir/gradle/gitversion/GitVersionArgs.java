package com.palantir.gradle.gitversion;

import java.util.Map;

class GitVersionArgs {
    private String prefix = "";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    // groovy closure invocation allows any number of args
    static GitVersionArgs fromGroovyClosure(Object... objects) {
        if (objects != null && objects.length > 0 && objects[0] instanceof Map) {
            GitVersionArgs instance = new GitVersionArgs();
            instance.setPrefix(((Map) objects[0]).get("prefix").toString());
            return instance;
        }

        return new GitVersionArgs();
    }
}
