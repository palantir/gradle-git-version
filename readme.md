<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-git-version"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

Git-Version Gradle Plugin
=========================
[![Build Status](https://circleci.com/gh/palantir/gradle-git-version.svg?style=shield)](https://circleci.com/gh/palantir/gradle-git-version)
[![Gradle Plugins Release](https://img.shields.io/github/release/palantir/gradle-git-version.svg)](https://plugins.gradle.org/plugin/com.palantir.git-version)

As of gradle-git-version 4.0, minimum supported JDK version is JDK 17 - in preparation for Gradle 9.0.

When applied, Git-Version adds two methods to the target project.

The first, called `gitVersion()`, mimics `git describe --tags --always --first-parent` to determine a version string.
It behaves exactly as `git describe --tags --always --first-parent` method behaves, except that when the repository is
in a dirty state, appends `.dirty` to the version string.

The second, called `versionDetails()`, returns an object containing the specific details of the version string:
the tag name, the commit count since the tag, the current commit hash of `HEAD`, and an optional branch name of `HEAD`.

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

Valid prefixes are defined by the regex `[/@]?([A-Za-z0-9]+[/@-])+`.
```
/Abc/
Abc@
foo-bar@
foo/bar-v2@
```

Tasks
-----
This plugin adds a `printVersion` task, which will echo the project's configured version
to standard-out.

### Overriding the `git` version with `GIT_VERSION` environment variable

Typically, most builds use `gradle-git-version` like so in the `build.gradle`:

```gradle
version gitVersion()
```

This will flow into publishing and a bunch of other builds. Sometimes you might not want to commit, other times you might need to pick a particular version. For whatever reason, you can do so via specifying `GIT_VERSION` as an environment variable when invoking Gradle:

```
> ./gradlew printVersion 
1.0.0

> GRADLE_VERSION=999 ./gradlew printVersion
999/
```

`Provider` APIs
---------------
`VersionDetails` and `getVersion` can be quite limiting, and it may not make sense to have this repository be the owner of all the different `git` invocations might require.

To that end, you can use a `GitInvoker`, it is a Gradle managed type, so you can inject it like so in plugins and other managed types:

```java
abstract class MyPlugin extends Plugin<Project> {
    @Nested
    protected abstract GitInvoker getGitInvoker();
    
    public final void apply(Project project) {
        Provider<String> branch = getGitInvoker().run("branch", "--show-current");
        
        // use this somehow
    }
} 
```

All calls are coalesced and run once for a given git root directory i.e. `<path>/.git`. So you can create as many instances and invoke as many times as required without taking a performance penalty.

### Handling errors yourself
This `Provider` will throw on resolution if the underlying `git` call fails. If you want finer control of the result, e.g. you want to handle a particular error, you can use `GitInvoker#invokeWithResult`:

```java
abstract class MyPlugin extends Plugin<Project> {
    @Nested
    protected abstract GitInvoker getGitInvoker();
    
    public final void apply(Project project) {
        Provider<GitExecOutput> branchResult = 
                getGitInvoker().runWithResult("branch", "--show-current");
        
        branchResult.map(result -> {
           if (result.exitCode() == 0) {
               return result.uncheckedStandardOutput();
           }
           
           if (result.standardError().contains("my special error")) {
               return "no branch";
           }
           
           // this will throw if the command failed for any other reason.
           result.assertNormalExitValue();
        });
        // use this somehow
    }
} 
```

### Common Git Operations managed type
We have provided some common `git` operations and exposed them as a managed type so you need not define all the `git` commands yourself. This is what powers the `gitVersion` and `versionDetails` calls internally. In a similar vein, you can inject `CommonGitOperations.Default`.

```java
abstract class MyPlugin extends Plugin<Project> {
    @Nested
    protected abstract CommonGitOperations.Default getCommonGitOperations();
    
    public final void apply(Project project) {
        Provider<String> branch = 
                getCommonGitOperations().branchName();
        // use this somehow
    }
} 
```

### Customizing `CommonGitOperations`

There are a lot of calls that eventually use the `prefix`, and it bleeds into all the entrypoints within `CommonGitOperations`. To that end, you can customize how `CommonGitOperations` is instantiated by instead injecting a `CommonGitOperations.Factory`. For example, to ensure that all calls that use the prefix in underlying `git` calls work correctly, you can set the `prefix` on the `CommonGitOperations.Factory.Parameters` type that is passed in to `CommonGitOperationsFactory#create`.

```java
abstract class MyPlugin extends Plugin<Project> {
    @Nested
    protected abstract CommonGitOperations.Factory getCommonGitOperationsFactory();
    
    public final void apply(Project project) {
        CommonGitOperations commonGitOperations = getCommonGitOperationsFactory().create(parameters -> {
           parameters.getPrefix().set("my-prefix@"); 
        });
        Provider<String> branch = commonGitOperations.branchName();
        // use this somehow
    }
} 
```

### Making your own domain specific operations type

If you have a particularly niche use case for some `git` commands that need to be used in other places, but don't make as much sense in this repository, you can make your own managed type!

```java
public abstract class MySpecialGitOperations {
    
    @Nested
    protected abstract GitInvoker getGitInvoker();
    
    
    @Nested
    protected abstract CommonGitOperations getCommonGitOperations();
    
    public final Provider<List<String>> lsTree() {
        return getGitInvoker().invoke("ls-tree", "-rz")
                .map(String::lines);
    }

    // By injecting CommonGitOperations, you can reuse predefined commands
    public final Provider<String> customAbbreviatedGitHash() {
        return getCommonGitOperations().fullGitHash()
                .map(fullGitHash -> fullGitHash.substring(0, 15));
    }
}
```

You can publish this as your own library and use it where you see fit!

☑️ Configuration cache
---
External process calls to `git` in this plugin are compatible with Gradle's [Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html).

### Caveats

> [!WARNING]
> Ensure that the build does not modify any `git` state e.g. calling `git commit`. If any methods in `VersionDetails` or `printVersion` rely on that state, they will serve whatever the value is at the time of resolving the `Provider` and never again until the end of the build.

As part of moving to Configuration Cache safe APIs, we use the configuration-time `Provider` APIs. A corollary of this is that if you query the *same* instance of a `Provider` it will **not** rerun the underlying `git` invocation.

As a result, if the state of the git repo changes within a single Gradle session (e.g. a task in the build does `git commit`), `VersionDetails` will reflect the outdated state of your repo.

However, changes to the state of your repo between Gradle builds i.e. two runs of Gradle...
```bash
> ./gradlew printVersion
git commit -m "new commit"

> ./gradlew printVersion
```

...are not affected by this as the `Provider`s are scoped to individual build invocations.

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
