/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.gitversion

import java.nio.file.Files

import org.eclipse.jgit.api.Git
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

    def 'unspecified when no annotated tags are present' () {
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

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(':printVersion\nunspecified\n')
    }

    def 'unspecified and dirty when no annotated tags are present and dirty content' () {
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
        dirtyContentFile << 'dirty-content'

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(':printVersion\nunspecified.dirty\n')
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

    def 'git describe and clean when symlink is present' () {

        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        File fileToLinkTo = temporaryFolder.newFile('fileToLinkTo')
        fileToLinkTo << 'content'
        Files.createSymbolicLink(temporaryFolder.getRoot().toPath().resolve('fileLink'), fileToLinkTo.toPath());

        File folderToLinkTo = temporaryFolder.newFolder('folderToLinkTo')
        new File(folderToLinkTo, 'dummyFile') << 'content'
        Files.createSymbolicLink(temporaryFolder.getRoot().toPath().resolve('folderLink'), folderToLinkTo.toPath());

        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(':printVersion\n1.0.0\n')
    }

    def 'version details null when no tags are present' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails() << {
                println versionDetails()
            }
        '''.stripIndent()
        Git git = Git.init().setDirectory(projectDir).call();

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output.contains(':printVersionDetails\nnull\n')
    }

    def 'version details on commit with a tag' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails() << {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().branchName
            }
        '''.stripIndent()
        gitIgnoreFile << 'build'
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('1.0.0').setName('1.0.0').call()

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n1.0.0\n0\n[a-z0-9]{10}\nmaster\n"
    }

    def 'version details when commit distance to tag is > 0' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails() << {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().branchName
            }

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
        buildResult.output =~ ":printVersionDetails\n1.0.0\n1\n[a-z0-9]{10}\nmaster\n"
    }

    def 'version details when detached HEAD mode' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails() << {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().branchName
            }

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

    private GradleRunner with(String... tasks) {
        GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(tasks)
    }

    def setup() {
        projectDir = temporaryFolder.root
        buildFile = temporaryFolder.newFile('build.gradle')
        gitIgnoreFile = temporaryFolder.newFile('.gitignore')
        dirtyContentFile = temporaryFolder.newFile('dirty')
    }

}
