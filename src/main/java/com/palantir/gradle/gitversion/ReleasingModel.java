package com.palantir.gradle.gitversion;

public enum ReleasingModel {
    /**
     * Indicates that releases are produced from develop. Tags
     * will all produce versions that match the tag.
     *
     * For example, tag 1.0.0 will produce a version 1.0.0.
     */
    DEVELOP,

    /**
     * Indicates that releases are produced from release branches.
     * Tags that match \d+\.\d+\.0 will produce versions using
     * <pre>git describe --long</pre>
     *
     * For example:
     *  - tag 1.0.0 will produce a version 1.0.0-0-g{hash}
     *  - tag 1.0.1 will produce a version 1.0.1
     *
     * This allows the develop/main branch to include x.y.0 tags
     * that aren't published as "releases", but instead published
     * as develop snapshots.
     */
    RELEASE_BRANCH
}
