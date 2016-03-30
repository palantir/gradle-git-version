Git-Version Gradle Plugin
=========================
[![Build Status](https://circleci.com/gh/palantir/gradle-git-version.svg?style=shield)](https://circleci.com/gh/palantir/gradle-git-version)
[![Gradle Plugins Release](https://img.shields.io/github/release/palantir/gradle-git-version.svg)](https://plugins.gradle.org/plugin/com.palantir.git-version)

When applied, Git-Version adds two methods to the target project.

The first, called `gitVersion()`, runs the JGit equivalent of `git describe` to determine a version string.
It behaves exactly as the JGit `git describe` method behaves, except that when the repository is in a dirty
state, appends `.dirty` to the version string.

The second, called `versionDetails()`, returns an object containing the specific details of the version string:
the tag name, and the commit count since the tag.

Usage
-----
Apply the plugin using standard Gradle convention:

    plugins {
        id 'com.palantir.git-version' version '<current version>'
    }

Set the version of a project by calling:

    version gitVersion()

You can get an object containing more detailed information by calling:

    def details = versionDetails()
    details.lastTag
    details.commitDistance

Tasks
-----
This plugin adds a `printVersion` task, which will echo the project's configured version
to standard-out.

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
