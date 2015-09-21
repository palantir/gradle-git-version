Git-Version Gradle Plugin
=========================
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

Tasks
-----
This plugin adds a `printVersion` task, which will echo the project's configured version
to standard-out.

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
