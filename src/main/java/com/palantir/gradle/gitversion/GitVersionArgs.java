package com.palantir.gradle.gitversion;

import com.google.common.base.Preconditions;

import java.util.Map;
import java.util.Objects;

class GitVersionArgs {
    private static final String PREFIX_REGEX = "[/@]?([A-Za-z]+[/@-])+";

    private String prefix = "";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        Preconditions.checkNotNull(prefix, "prefix must not be null");

        Preconditions.checkState(prefix.matches(PREFIX_REGEX),
                "Specified prefix `" + prefix + "` does not match the allowed format regex `" + PREFIX_REGEX + "`.");

        this.prefix = prefix;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        GitVersionArgs that = (GitVersionArgs) other;
        return Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix);
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
