package com.palantir.gradle.gitversion;

public interface IVersionDetails {
    String getVersion();

    boolean getIsCleanTag();

    int getCommitDistance();

    String getLastTag();

    String getGitHash();

    /** @return full 40-character Git commit hash */
    String getGitHashFull();

    String getBranchName();
}
