Git-Version Gradle Plugin
=========================
[![Build Status](https://travis-ci.org/palantir/gradle-git-version.svg?branch=develop)](https://travis-ci.org/palantir/gradle-git-version)

When applied, Git-Version adds a method to the target project called `gitVersion()` that
runs the JGit equivalent of `git describe` to determine a version string. It behaves exactly 
as the JGit `git describe` method behaves, except that when the repository is in a dirty 
state, appends `.dirty` to the version string.

Usage
-----
Apply the plugin using standard Gradle convention:

    plugins {
        id 'com.palantir.git-version'
    }

Set the version of a project by calling:

    version gitVersion()

`gitVersion` function takes two optional positional parameters :
    1 `boolean longDescription` just like `git describe --long` will always show the long
        format if set to true. It is passed directly to DescribeCommand.setLong
    2 `String target` takes any valid target String. See [Repository.resolve(String target)](http://download.eclipse.org/jgit/site/4.1.1.201511131810-r/apidocs/index.html)
        for valid options.

An example using the long format :

    version gitVersion(true)

An example using the short format and specifying a target :

    version gitVersion(false, 'master')

Tasks
-----
This plugin adds a `printVersion` task, which will echo the project's configured version
to standard-out.

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
