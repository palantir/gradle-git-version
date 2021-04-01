<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-git-version"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

Git-Version Gradle Plugin
=========================
[![Build Status](https://circleci.com/gh/palantir/gradle-git-version.svg?style=shield)](https://circleci.com/gh/palantir/gradle-git-version)
[![Gradle Plugins Release](https://img.shields.io/github/release/palantir/gradle-git-version.svg)](https://plugins.gradle.org/plugin/com.palantir.git-version)

When applied, Git-Version adds two methods to the target project.

The first, called `gitVersion()`, mimics `git describe --tags --always --first-parent` to determine a version string.
It behaves exactly as `git describe --tags --always --first-parent` method behaves, except that when the repository is
in a dirty state, appends `.dirty` to the version string.

The second, called `versionDetails()`, returns an object containing the specific details of the version string:
the tag name, the commit count since the tag, the current commit hash of HEAD, and an optional branch name of HEAD.

Usage
-----
Apply the plugin using standard Gradle convention:

**Groovy**
```groovy
plugins {
    id 'com.palantir.git-version' version '<current version>'
}
```

**Kotlin**
```kotlin
plugins {
    id("com.palantir.git-version") version "<current version>"
}
```

Set the version of a project by calling:

**Groovy**
```groovy
version gitVersion()
```

**Kotlin**
```kotlin
val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()
```

You can get an object containing more detailed information by calling:

**Groovy**
```groovy
def details = versionDetails()
details.lastTag
details.commitDistance
details.gitHash
details.gitHashFull // full 40-character Git commit hash
details.branchName // is null if the repository in detached HEAD mode
details.isCleanTag
```

**Kotlin**
```kotlin
val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
val details = versionDetails()
details.lastTag
details.commitDistance
details.gitHash
details.gitHashFull // full 40-character Git commit hash
details.branchName // is null if the repository in detached HEAD mode
details.isCleanTag
```

You can optionally search a subset of tags with `prefix`. Example when the tag is my-product@2.15.0:

**Groovy**
```groovy
gitVersion(prefix:'my-product@') // -> 2.15.0
```

**Kotlin**
```kotlin
val gitVersion: groovy.lang.Closure<String> by extra
gitVersion(mapOf("prefix" to "my-product@")) // -> 2.15.0
```

Valid prefixes are defined by the regex `[/@]?([A-Za-z]+[/@-])+`.
```
/Abc/
Abc@
foo-bar@
foo/bar@
```

Tasks
-----
This plugin adds a `printVersion` task, which will echo the project's configured version
to standard-out.

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
