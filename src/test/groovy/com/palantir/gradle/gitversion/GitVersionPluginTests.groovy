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

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class GitVersionPluginTests extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir
    File buildFile
    File gitIgnoreFile
    File dirtyContentFile
    File settingsFile

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
        File rootFolder = temporaryFolder.root
        projectDir = temporaryFolder.newFolder('level1', 'level2')
        buildFile = temporaryFolder.newFile('level1/level2/build.gradle')
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        new File(projectDir, 'settings.gradle').createNewFile()
        Git git = Git.init().setDirectory(rootFolder).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        when:
        // will build the project at projectDir
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
        Git git = Git.init().setDirectory(projectDir).call();

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(false).setName('1.0.0').call()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"
        String master = git.getRepository().getFullBranch();
        Ref hotfixBranch = git.branchCreate().setName("hotfix").call()
        git.checkout().setName(hotfixBranch.getName()).call()
        git.commit().setMessage("hot fix for issue").call()
        git.tag().setAnnotated(true).setMessage('1.0.0-hotfix').setName('1.0.0-hotfix').call()

        // switch back to main branch and merge hotfix branch into main branch
        git.checkout().setName(master).call()
        git.merge().include(git.getRepository().getRefDatabase().findRef("hotfix"))
                .setFastForward(MergeCommand.FastForwardMode.NO_FF).setMessage("merge commit").call()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"
        String master = git.getRepository().getFullBranch();
        Ref hotfixBranch = git.branchCreate().setName("hotfix").call()
        git.checkout().setName(hotfixBranch.getName()).call()
        git.commit().setMessage("hot fix for issue").call()
        git.tag().setAnnotated(true).setMessage('1.0.0-hotfix').setName('1.0.0-hotfix').call()

        // switch back to main branch and merge hotfix branch into main branch
        git.checkout().setName(master).call()
        git.merge().include(git.getRepository().getRefDatabase().findRef("hotfix"))
                .setFastForward(MergeCommand.FastForwardMode.NO_FF).setMessage("merge commit").call()

        // tag merge commit on main branch as 2.0.0
        git.tag().setAnnotated(true).setMessage('2.0.0').setName('2.0.0').call()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()
        dirtyContentFile << 'dirty-content'

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        String sha = git.commit().setMessage('initial commit').call().getName().subSequence(0, 7)

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()
        git.commit().setMessage('commit 2').call()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        def commit1 = git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()
        git.commit().setMessage('commit 2').call()
        git.checkout().setName(commit1.getId().getName()).call()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('my-product@1.0.0').setName('my-product@1.0.0').call()
        git.commit().setMessage('commit 2').call()
        git.tag().setAnnotated(true).setMessage('1.1.0').setName('1.1.0').call()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()
        dirtyContentFile << 'dirty-content'
        git.add().addFilepattern('.').call()
        RevCommit latestCommit = git.commit().setMessage('added some stuff').call()

        when:
        BuildResult buildResult = with('printVersion').build()
        String commitSha = latestCommit.getName()

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
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(false).setName('1.0.0').call()
        dirtyContentFile << 'dirty-content'
        git.add().addFilepattern('.').call()
        RevCommit latestCommit = git.commit().setMessage('added some stuff').call()

        when:
        BuildResult buildResult = with('printVersion').build()
        String commitSha = latestCommit.getName()

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

        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        File subDir = temporaryFolder.newFolder('sub');
        Git subGit = Git.init().setDirectory(subDir).call();
        File subDirty = new File(subDir, 'subDirty')
        subDirty.createNewFile()
        subGit.add().addFilepattern('.').call()
        subGit.commit().setMessage('initial commit sub').call()
        subGit.tag().setAnnotated(true).setMessage('8.8.8').setName('8.8.8').call()

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
        Git git = Git.init().setDirectory(projectDir).call()
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(false).setName('1.0.0').call()
        git.tag().setAnnotated(true).setName('2.0.0').call()
        git.tag().setAnnotated(false).setName('3.0.0').call()

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
        Git git = Git.init().setDirectory(projectDir).call()
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        PersonIdent ident = new PersonIdent("name", "email@example.com")
        git.tag().setAnnotated(true).setTagger(new PersonIdent(ident, new Date() - 2)).setName('1.0.0').call()
        git.tag().setAnnotated(true).setTagger(new PersonIdent(ident, new Date())).setName('2.0.0').call()
        git.tag().setAnnotated(true).setTagger(new PersonIdent(ident, new Date() - 1)).setName('3.0.0').call()

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
        Git git = Git.init().setDirectory(projectDir).call()
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(false).setName('2.0.0').call()
        git.tag().setAnnotated(false).setName('1.0.0').call()
        git.tag().setAnnotated(false).setName('3.0.0').call()

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
        Git git = Git.init().setDirectory(projectDir).call()
        git.add().addFilepattern('.').call()
        RevCommit latestCommit = git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        int depth = 100
        for (int i = 0; i < depth; i++) {
            git.add().addFilepattern('.').call()
            latestCommit = git.commit().setMessage('commit-' + i).call()
        }

        when:
        BuildResult buildResult = with('printVersion').build()
        String commitSha = latestCommit.getName()

        then:
        buildResult.output.contains(":printVersion\n1.0.0-${depth}-g${commitSha.substring(0, 7)}\n")
    }

    def 'does not crash when setting build scan custom values when Gradle 5 build scan plugin is applied'() {
        when:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
                id 'com.gradle.build-scan' version '3.2'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        Git git = Git.init().setDirectory(projectDir).call()
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        then:
        with(Optional.of('5.6.4'), 'printVersion').build()
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
        Git git = Git.init().setDirectory(projectDir).call()
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        then:
        with('printVersion').build()
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
        Git git = Git.init().setDirectory(projectDir).call()
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

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

    private static shortSha(Git git, String commitish) {
        final int VERSION_ABBR_LENGTH = 7
        git.getRepository().getRef(commitish).getObjectId().abbreviate(VERSION_ABBR_LENGTH).name()
    }

    def setup() {
        projectDir = temporaryFolder.root
        buildFile = temporaryFolder.newFile('build.gradle')
        settingsFile = temporaryFolder.newFile('settings.gradle')
        gitIgnoreFile = temporaryFolder.newFile('.gitignore')
        dirtyContentFile = temporaryFolder.newFile('dirty')
        settingsFile << '''
            rootProject.name = 'gradle-test'
        '''.stripIndent()
        gitIgnoreFile << '.gradle\n'
    }

}
