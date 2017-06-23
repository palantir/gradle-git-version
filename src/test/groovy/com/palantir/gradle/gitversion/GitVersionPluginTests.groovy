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

import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.lib.Ref

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

    def 'short sha1 when no annotated tags are present' () {
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

        String expected = shortSha(git, "HEAD")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(':printVersion\n'+expected+'\n')
    }

    def 'short sha1 and dirty when no annotated tags are present and dirty content' () {
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
        String expected = shortSha(git, "HEAD")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(':printVersion\n'+expected+'.dirty\n')
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
        git.merge().include(git.getRepository().getRef("hotfix")).setFastForward(MergeCommand.FastForwardMode.NO_FF).setMessage("merge commit").call()

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
        git.merge().include(git.getRepository().getRef("hotfix")).setFastForward(MergeCommand.FastForwardMode.NO_FF).setMessage("merge commit").call()

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

    def 'version details not null when no tags are present' () {
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
        buildResult.output.contains(':printVersionDetails\ncom.palantir.gradle.gitversion.VersionDetails(null, null, null, false)\n')
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
                println versionDetails().isCleanTag
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
        buildResult.output =~ ":printVersionDetails\n1.0.0\n0\n[a-z0-9]{10}\nmaster\ntrue\n"
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
                println versionDetails().isCleanTag
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
        buildResult.output =~ ":printVersionDetails\n1.0.0\n1\n[a-z0-9]{10}\nmaster\nfalse\n"
    }

    def 'isCleanTag should be false when repo dirty on a tag checkout' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            task printVersionDetails() << {
                println versionDetails().isCleanTag
            }

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

    def 'version includes tag if includePrefix is true' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion(prefix:"my-product", includePrefix:true)
            task printVersionDetails() << {
                println versionDetails(prefix:"my-product", includePrefix:true).lastTag
            }
        '''.stripIndent()
        gitIgnoreFile << 'build'
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('my-product1.0.0').setName('my-product1.0.0').call()

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\nmy-product1.0.0\n"
    }

    def 'version filters out tags not matching prefix' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion(prefix:"my-product")
            task printVersionDetails() << {
                println versionDetails(prefix:"my-product").lastTag
            }
        '''.stripIndent()
        gitIgnoreFile << 'build'
        Git git = Git.init().setDirectory(projectDir).call();
        git.add().addFilepattern('.').call()
        git.commit().setMessage('initial commit').call()
        git.tag().setAnnotated(true).setMessage('my-product1.0.0').setName('my-product1.0.0').call()
        git.commit().setMessage('commit 2').call()
        git.tag().setAnnotated(true).setMessage('1.1.0').setName('1.1.0').call()

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n1.0.0\n"
    }

    private GradleRunner with(String... tasks) {
        GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(tasks)
    }

    private static shortSha(Git git, String commitish) {
        final int VERSION_ABBR_LENGTH = 7
        git.getRepository().getRef(commitish).getObjectId().abbreviate(VERSION_ABBR_LENGTH).name()
    }

    def setup() {
        projectDir = temporaryFolder.root
        buildFile = temporaryFolder.newFile('build.gradle')
        gitIgnoreFile = temporaryFolder.newFile('.gitignore')
        dirtyContentFile = temporaryFolder.newFile('dirty')
    }

}
