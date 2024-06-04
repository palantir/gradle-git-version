/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.gitversion

import java.nio.file.Files
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification

class GitVersionPluginTests extends Specification {

    File temporaryFolder
    File projectDir
    File buildFile
    File gitIgnoreFile
    File dirtyContentFile
    File settingsFile

    def setup() {
        temporaryFolder = File.createTempDir('GitVersionPluginTest')
        projectDir = temporaryFolder
        buildFile = new File(temporaryFolder, 'build.gradle')
        buildFile.createNewFile()
        settingsFile = new File(temporaryFolder, 'settings.gradle')
        settingsFile.createNewFile()
        gitIgnoreFile = new File(temporaryFolder, '.gitignore')
        gitIgnoreFile.createNewFile()
        dirtyContentFile = new File(temporaryFolder, 'dirty')
        dirtyContentFile.createNewFile()
        settingsFile << '''
            rootProject.name = 'gradle-test'
        '''.stripIndent()
        gitIgnoreFile << '.gradle\n'
    }

    def 'exception when project root does not have a git repo' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()

        when:
        BuildResult buildResult = with('printVersion').buildAndFail()

        then:
        buildResult.output.contains('> Cannot find \'.git\' directory')
    }

    def 'git describe works when git repo is multiple levels up' () {
        given:
        File rootFolder = temporaryFolder
        projectDir = Files.createDirectories(rootFolder.toPath().resolve('level1/level2')).toFile()
        buildFile = new File(projectDir, 'build.gradle')
        buildFile.createNewFile()
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        new File(projectDir, 'settings.gradle').createNewFile()
        GitImpl git = new GitImpl(rootFolder, new GitVersionArgs(""), true)
        git.runGitCommand("init", rootFolder.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit","-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        when:
        // will build the project at projectDir
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0\n")
    }

    def 'git describe works when using worktree' () {
        given:
        File rootFolder = temporaryFolder
        projectDir = Files.createDirectories(rootFolder.toPath().resolve('worktree')).toFile()
        File originalDir = Files.createDirectories(rootFolder.toPath().resolve('original')).toFile()
        buildFile = new File(originalDir, 'build.gradle')
        buildFile.createNewFile()
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        new File(originalDir, 'settings.gradle').createNewFile()
        File originalGitIgnoreFile = new File(originalDir, ".gitignore")
        originalGitIgnoreFile.createNewFile()
        originalGitIgnoreFile << '.gradle\n'
        GitImpl git = new GitImpl(originalDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", originalDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit","-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")
        git.runGitCommand("branch", "newbranch")
        git.runGitCommand("worktree", "add", "../worktree", "newbranch")

        when:
        // will build the project at projectDir
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0\n")
    }

    def 'git version can be applied on sub modules' () {
        given:
        File subModuleDir = Files.createDirectories(projectDir.toPath().resolve('submodule')).toFile()
        File subModuleBuildFile = new File(subModuleDir, 'build.gradle')
        subModuleBuildFile.createNewFile()
        subModuleBuildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()

        settingsFile << '''
            include 'submodule'
        '''.stripIndent()

        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit","-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0\n")
    }

    def 'unspecified when no tags are present' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()

        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(':printVersion\nunspecified\n')
    }

    def 'git describe when annotated tag is present' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0\n")
    }

    def 'git describe when lightweight tag is present' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "1.0.0")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0\n")
    }

    def 'git describe when annotated tag is present with merge commit' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'

        // create repository with a single commit tagged as 1.0.0
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"
        String master = git.getCurrentHeadFullHash().subSequence(0, 7)
        git.runGitCommand("checkout", "-b", "hotfix")
        git.runGitCommand("commit", "-m", "hot fix for issue", "--allow-empty")
        git.runGitCommand("tag", "-a", "1.0.0-hotfix", "-m", "1.0.0-hotfix")
        String commitId = git.getCurrentHeadFullHash()
        // switch back to main branch and merge hotfix branch into main branch
        git.runGitCommand("checkout", master)
        git.runGitCommand("merge", commitId, "--no-ff", "-m", "merge commit")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output =~ ":printVersion\n1.0.0-1-g[a-z0-9]{7}\n"
    }

    def 'git describe when annotated tag is present after merge commit' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'

        // create repository with a single commit tagged as 1.0.0
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"

        String master = git.getCurrentHeadFullHash().subSequence(0, 7)
        git.runGitCommand("checkout", "-b", "hotfix")
        git.runGitCommand("commit", "-m", "hot fix for issue", "--allow-empty")
        git.runGitCommand("tag", "-a", "1.0.0-hotfix", "-m", "1.0.0-hotfix")
        String commitId = git.getCurrentHeadFullHash()

        // switch back to main branch and merge hotfix branch into main branch
        git.runGitCommand("checkout", master)
        git.runGitCommand("merge", commitId, "--no-ff", "-m", "merge commit")

        // tag merge commit on main branch as 2.0.0
        git.runGitCommand("tag", "-a", "2.0.0", "-m", "2.0.0")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output =~ ":printVersion\n2.0.0\n"
    }

    def 'git describe and dirty when annotated tag is present and dirty content' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")
        dirtyContentFile << 'dirty-content'

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        //buildResult.output.contains(projectDir.getAbsolutePath())
        buildResult.output.contains(':printVersion\n1.0.0.dirty\n')
    }

    def 'version details on commit with a tag' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails { doLast {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().gitHashFull
                println versionDetails().branchName
                println versionDetails().isCleanTag
            }}
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n1.0.0\n0\n[a-z0-9]{10}\n[a-z0-9]{40}\nmaster\ntrue\n"
    }

    def 'version details can be accessed using extra properties method' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails { doLast {
                println project.getExtensions().getExtraProperties().get('versionDetails')().lastTag
                println project.getExtensions().getExtraProperties().get('gitVersion')()
            }}
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        String sha = git.getCurrentHeadFullHash().subSequence(0, 7)

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n${sha}\n${sha}\n"
    }

    def 'version details when commit distance to tag is > 0' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails { doLast {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().branchName
                println versionDetails().isCleanTag
            }}

        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")
        git.runGitCommand("commit", "-m", "'commit 2'", "--allow-empty")

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n1.0.0\n1\n[a-z0-9]{10}\nmaster\nfalse\n"
    }

    def 'isCleanTag should be false when repo dirty on a tag checkout' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails { doLast {
                println versionDetails().isCleanTag
            }}

        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        dirtyContentFile << 'dirty-content'

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\nfalse\n"
    }

    def 'version details when detached HEAD mode' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails { doLast {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().branchName
            }}

        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        String commitId = git.getCurrentHeadFullHash()
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")
        git.runGitCommand("commit", "-m", "'commit 2'", "--allow-empty")
        git.runGitCommand("checkout", commitId)

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n1.0.0\n0\n[a-z0-9]{10}\nnull\n"
    }

    def 'version filters out tags not matching prefix and strips prefix' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion(prefix:"my-product@")
            task printVersionDetails { doLast {
                println versionDetails(prefix:"my-product@").lastTag
            }}
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "my-product@1.0.0", "-m", "my-product@1.0.0")
        git.runGitCommand("commit", "-m", "'commit 2'", "--allow-empty")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n1.0.0\n"
    }

    def 'git describe with commit after annotated tag' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")
        dirtyContentFile << 'dirty-content'
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'add some stuff'")
        String commitSha = git.getCurrentHeadFullHash()

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0-1-g${commitSha.substring(0, 7)}\n")
    }

    def 'git describe with commit after lightweight tag' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "1.0.0")
        dirtyContentFile << 'dirty-content'
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'add some stuff'")
        String commitSha = git.getCurrentHeadFullHash()

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0-1-g${commitSha.substring(0, 7)}\n")
    }

    def 'test subproject version' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            subprojects {
                apply plugin: 'com.palantir.git-version'
                version gitVersion()
            }
        '''.stripIndent()

        settingsFile << "include 'sub'"

        gitIgnoreFile << 'build\n'
        gitIgnoreFile << 'sub\n'

        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        File subDir = Files.createDirectory(temporaryFolder.toPath().resolve('sub')).toFile()
        GitImpl subGit = new GitImpl(subDir, new GitVersionArgs(""), true)
        subGit.runGitCommand("init", subDir.toString())
        File subDirty = new File(subDir, 'subDirty')
        subDirty.createNewFile()
        subGit.runGitCommand("add", ".")
        subGit.runGitCommand("commit", "-m", "'initial commit sub'")
        subGit.runGitCommand("tag", "-a", "8.8.8", "-m", "8.8.8")

        when:
        BuildResult buildResult = with('printVersion', ':sub:printVersion').build()

        then:
        buildResult.output.contains ":printVersion\n1.0.0\n"
        buildResult.output.contains ":sub:printVersion\n8.8.8\n"
    }

    def 'test multiple tags on same commit - annotated tag is chosen' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            subprojects {
                apply plugin: 'com.palantir.git-version'
                version gitVersion()
            }
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "1.0.0")
        git.runGitCommand("tag", "-a", "2.0.0", "-m", "2.0.0")
        git.runGitCommand("tag", "3.0.0")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n2.0.0\n")
    }

    def 'test multiple tags on same commit - most recent annotated tag' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            subprojects {
                apply plugin: 'com.palantir.git-version'
                version gitVersion()
            }
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        Date d1 = new Date() - 2;
        HashMap<String, String> envvar1 = new HashMap<>();
        envvar1.put("GIT_COMMITTER_DATE", d1.toString())
        git.runGitCommand(envvar1, "-c", "user.name='name'", "-c", "user.email=email@example.com", "tag", "-a", "1.0.0", "-m", "1.0.0")
        Date d2 = new Date();
        HashMap<String, String> envvar2 = new HashMap<>();
        envvar2.put("GIT_COMMITTER_DATE", d2.toString())
        git.runGitCommand(envvar2, "-c", "user.name='name'", "-c", "user.email=email@example.com", "tag", "-a", "2.0.0", "-m", "2.0.0")
        Date d3 = new Date() - 1;
        HashMap<String, String> envvar3 = new HashMap<>();
        envvar3.put("GIT_COMMITTER_DATE", d3.toString())
        git.runGitCommand(envvar3, "-c", "user.name='name'", "-c", "user.email=email@example.com", "tag", "-a", "3.0.0", "-m", "3.0.0")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n2.0.0\n")
    }

    def 'test multiple tags on same commit - smaller unannotated tag is chosen' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            subprojects {
                apply plugin: 'com.palantir.git-version'
                version gitVersion()
            }
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "2.0.0")
        git.runGitCommand("tag", "1.0.0")
        git.runGitCommand("tag", "3.0.0")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0\n")
    }

    def 'test tag set on deep commit' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")
        String latestCommit = git.getCurrentHeadFullHash()

        int depth = 100
        for (int i = 0; i < depth; i++) {
            git.runGitCommand("add", ".")
            git.runGitCommand("commit", "-m", "commit-"+Integer.toString(i), "--allow-empty")
            latestCommit = git.getCurrentHeadFullHash()
        }

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0-${depth}-g${latestCommit.substring(0, 7)}\n")
    }

    def 'does not crash when setting build scan custom values when Gradle 6 enterprise plugin 3.2 is applied'() {
        when:
        settingsFile.text = '''
            plugins {
              id "com.gradle.enterprise" version "3.2"
            }
        '''.stripIndent() + settingsFile.text

        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        then:
        with('printVersion').build()
    }


    def 'does not crash when setting build scan custom values when Gradle 7 build scan plugin is applied'() {
        when:
        settingsFile.text = '''
            plugins {
              id "com.gradle.enterprise" version "3.2"
            }
        '''.stripIndent() + settingsFile.text

        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        then:
        with(Optional.of('7.4.2'), 'printVersion').build()
    }

    def 'does not crash when setting build scan custom values when Gradle 6 enterprise plugin 3.1 is applied'() {
        when:
        settingsFile.text = '''
            plugins {
              id "com.gradle.enterprise" version "3.1"
            }
        '''.stripIndent() + settingsFile.text

        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        GitImpl git = new GitImpl(projectDir, new GitVersionArgs(""), true)
        git.runGitCommand("init", projectDir.toString())
        git.runGitCommand("add", ".")
        git.runGitCommand("commit", "-m", "'initial commit'")
        git.runGitCommand("tag", "-a", "1.0.0", "-m", "1.0.0")

        then:
        with('printVersion').build()
    }

    private GradleRunner with(String... tasks) {
        return with(Optional.empty(), tasks)
    }

    private GradleRunner with(Optional<String> gradleVersion, String... tasks) {
        List<String> arguments = new ArrayList<>(['--stacktrace'])
        arguments.addAll(tasks)

        def gradleRunner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir)
                .withArguments(arguments)

        gradleVersion.ifPresent({ version -> gradleRunner.withGradleVersion(version) })

        return gradleRunner
    }
}
