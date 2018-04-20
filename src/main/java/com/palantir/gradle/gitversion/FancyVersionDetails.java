package com.palantir.gradle.gitversion;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FancyVersionDetails implements IVersionDetails {

    private static final int VERSION_ABBR_LENGTH = 10;

    private final Git git;
    private final GitVersionArgs args;

    private volatile String maybeCachedDescription = null;

    public FancyVersionDetails(Git git, GitVersionArgs args) {
        this.git = git;
        this.args = args;
    }

    private String description() {
        if (maybeCachedDescription == null) {
            String rawDescription = expensiveComputeRawDescription();
            this.maybeCachedDescription = rawDescription == null ?
                    rawDescription : rawDescription.replaceFirst("^" + args.getPrefix(), "");
        }

        return maybeCachedDescription;
    }

    private String expensiveComputeRawDescription() {
        String nativeGitDescribe = new NativeGitDescribe(git.getRepository().getDirectory(), git)
                .describe(args.getPrefix());
        String jgitDescribe = new JGitDescribe(git)
                .describe(args.getPrefix());

        // If native failed, return JGit one
        if (nativeGitDescribe == null) {
            return jgitDescribe;
        }

        // If native succeeded, make sure it's same as JGit one
        if (!nativeGitDescribe.equals(jgitDescribe)) {
            throw new IllegalStateException(String.format("Inconsistent git describe: native was %s and jgit was %s. " +
                    "Please report this on github.com/palantir/gradle-git-version", nativeGitDescribe, jgitDescribe));
        }

        return jgitDescribe;
    }

    @Override
    public String getVersion() {
        if (description() == null) {
            return "unspecified";
        }

        return description() + (isClean() ? "" : ".dirty");
    }

    @Override
    public boolean getIsCleanTag() {
        return isClean() && descriptionIsPlainTag();
    }

    private boolean descriptionIsPlainTag() {
        return !Pattern.matches(".*g.?[0-9a-fA-F]{3,}", description());
    }

    @Override
    public int getCommitDistance() {
        if (descriptionIsPlainTag()) {
            return 0;
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        return match.matches() ? Integer.valueOf(match.group(2)) : null;
    }

    @Override
    public String getLastTag() {
        if (descriptionIsPlainTag()) {
            return description();
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        return match.matches() ? match.group(1) : null;
    }

    private boolean isClean() {
        try {
            return git.status().call().isClean();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getGitHash() {
        String gitHashFull = getGitHashFull();
        if (gitHashFull == null) {
            return null;
        }

        return gitHashFull.substring(0, VERSION_ABBR_LENGTH);
    }

    @Override
    public String getGitHashFull() {
        try {
            ObjectId objectId = git.getRepository().findRef(Constants.HEAD).getObjectId();
            if (objectId == null) {
                return null;
            }

            return objectId.name();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getBranchName() {
        try {
            Ref ref = git.getRepository().findRef(git.getRepository().getBranch());
            if (ref == null) {
                return null;
            }

            return ref.getName().substring(Constants.R_HEADS.length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
