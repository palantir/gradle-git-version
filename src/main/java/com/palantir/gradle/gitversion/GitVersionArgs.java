package com.palantir.gradle.gitversion;

import java.util.Map;

import com.google.common.base.Preconditions;

class GitVersionArgs {
    private static final String PREFIX_REGEX = "[/@]?([A-Za-z]+[/@-])+";

    private String prefix = "";
    private ReleasingModel model = ReleasingModel.DEVELOP;

    public String getPrefix() {
        return prefix;
    }

    public ReleasingModel getModel() {
        return model;
    }

    public void setPrefix(String prefix) {
        Preconditions.checkNotNull(prefix, "prefix must not be null");

        Preconditions.checkState(prefix.matches(PREFIX_REGEX),
                "Specified prefix `" + prefix + "` does not match the allowed format regex `" + PREFIX_REGEX + "`.");

        this.prefix = prefix;
    }

    public void setModel(String model) {
        Preconditions.checkNotNull(model, "model must not be null");
        this.model = ReleasingModel.valueOf(model);
    }

    // groovy closure invocation allows any number of args
    static GitVersionArgs fromGroovyClosure(Object... objects) {
        if (objects != null && objects.length > 0 && objects[0] instanceof Map) {
            GitVersionArgs instance = new GitVersionArgs();
            Map object = (Map) objects[0];
            Object prefix = object.get("prefix");
            if (prefix != null) {
                instance.setPrefix(prefix.toString());
            }
            Object model = object.get("model");
            if (model != null) {
                instance.setModel(model.toString());
            }
            return instance;
        }

        return new GitVersionArgs();
    }
}
