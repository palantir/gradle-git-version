package com.palantir.gradle.gitversion;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VersionDetails {

    private static final Logger log = LoggerFactory.getLogger(VersionDetails.class);
    private static final int VERSION_ABBR_LENGTH = 10;

    private final Git git;
    private final GitVersionArgs args;

    private volatile Boolean maybeCachedIsClean = null;
    private volatile String maybeCachedDescription = null;
    private volatile String maybeCachedGitHashFull = null;
    private volatile String maybeCachedBranchName = null;

    VersionDetails(Git git, GitVersionArgs args) {
        this.git = git;
        this.args = args;
    }

    public String getVersion() {
        if (description() == null) {
            return "unspecified";
        }

        return description() + (isClean() ? "" : ".dirty");
    }

    private boolean isClean() {
        if (maybeCachedIsClean != null) {
            return maybeCachedIsClean;
        }

        try {
            maybeCachedIsClean = git.status().call().isClean();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }

        return maybeCachedIsClean;
    }

    private String description() {
        if (maybeCachedDescription != null) {
            return maybeCachedDescription;
        }

        String rawDescription = expensiveComputeRawDescription();
        maybeCachedDescription = rawDescription == null ?
                null : rawDescription.replaceFirst("^" + args.getPrefix(), "");
        return maybeCachedDescription;
    }

    private String expensiveComputeRawDescription() {
        if (isRepoEmpty()) {
            log.debug("Repository is empty");
            return null;
        }

        return new JGitDescribe(git).describe(args.getPrefix());
    }

    private boolean isRepoEmpty() {
        // back-compat: the JGit "describe" command throws an exception in repositories with no commits, so call it
        // first to preserve this behavior in cases where this call would fail but native "git" call does not.
        try {
            git.describe().call();
            return false;
        } catch (Exception ignored) {
            return true;
        }
    }

    public boolean getIsCleanTag() {
        return isClean() && descriptionIsPlainTag();
    }

    private boolean descriptionIsPlainTag() {
        return !Pattern.matches(".*g.?[0-9a-fA-F]{3,}", description());
    }

    public int getCommitDistance() {
        if (descriptionIsPlainTag()) {
            return 0;
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        return match.matches() ? Integer.valueOf(match.group(2)) : null;
    }

    public String getLastTag() {
        if (descriptionIsPlainTag()) {
            return description();
        }

        Matcher match = Pattern.compile("(.*)-([0-9]+)-g.?[0-9a-fA-F]{3,}").matcher(description());
        return match.matches() ? match.group(1) : null;
    }

    public String getGitHash() throws IOException {
        String gitHashFull = getGitHashFull();
        if (gitHashFull == null) {
            return null;
        }

        return gitHashFull.substring(0, VERSION_ABBR_LENGTH);
    }

    public String getGitHashFull() throws IOException {
        if (maybeCachedGitHashFull != null) {
            return maybeCachedGitHashFull;
        }

        ObjectId objectId = git.getRepository().findRef(Constants.HEAD).getObjectId();
        if (objectId == null) {
            return null;
        }

        maybeCachedGitHashFull = objectId.name();

        return maybeCachedGitHashFull;
    }

    public String getBranchName() throws IOException {
        if (maybeCachedBranchName != null) {
            return maybeCachedBranchName;
        }

        Ref ref = git.getRepository().findRef(git.getRepository().getBranch());
        if (ref == null) {
            return null;
        }

        maybeCachedBranchName = ref.getName().substring(Constants.R_HEADS.length());

        return maybeCachedBranchName;
    }

    @Override
    public String toString() {
        try {
            return String.format("VersionDetails(%s, %s, %s, %s, %s)",
                    getVersion(),
                    getGitHash(),
                    getGitHashFull(),
                    getBranchName(),
                    getIsCleanTag()
            );
        } catch (IOException e) {
            return null;
        }
    }
}
