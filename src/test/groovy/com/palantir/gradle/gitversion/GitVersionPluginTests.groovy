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
import java.nio.charset.StandardCharsets;
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
        
        gitInit(rootFolder)
        runGitCmd(rootFolder, "add", ".")
        runGitCmd(rootFolder, "commit","-m", "'initial commit'")
        runGitCmd(rootFolder, "tag", "-a", "1.0.0", "-m", "1.0.0")

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

        gitInit(originalDir)
        runGitCmd(originalDir, "add", ".")
        runGitCmd(originalDir, "commit","-m", "'initial commit'")
        runGitCmd(originalDir, "tag", "-a", "1.0.0", "-m", "1.0.0")
        runGitCmd(originalDir, "branch", "newbranch")
        runGitCmd(originalDir, "worktree", "add", "../worktree", "newbranch")

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit","-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")

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

        gitInit(projectDir)

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
        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0\n")
    }

    def 'gitVersion() uses GIT_VERSION environment variable if it is set' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
        '''.stripIndent()
        gitIgnoreFile << 'build'
        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")

        when:
        BuildResult normalResult = with('printVersion').build()

        then:
        normalResult.output.contains(":printVersion\n1.0.0\n")

        when:
        BuildResult overriddenResult = with(Optional.empty(), Optional.of(Map.of("GIT_VERSION", "999")),'printVersion').build()

        then:
        overriddenResult.output.contains(":printVersion\n999\n")
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
        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "1.0.0")

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
        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "initial commit")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"
        String master = runGitCmd(projectDir, "rev-parse", "--short", "HEAD").trim()
        runGitCmd(projectDir, "checkout", "-b", "hotfix")
        runGitCmd(projectDir, "commit", "-m", "hot fix for issue", "--allow-empty")
        runGitCmd(projectDir, "tag", "-a", "1.0.0-hotfix", "-m", "1.0.0-hotfix")
        String commitId = runGitCmd(projectDir, "rev-parse", "HEAD").trim()
        // switch back to main branch and merge hotfix branch into main branch
        runGitCmd(projectDir, "checkout", master)
        runGitCmd(projectDir, "merge", commitId, "--no-ff", "-m", "merge commit")

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "initial commit")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"

        String master = runGitCmd(projectDir, "rev-parse", "--short", "HEAD").trim()
        runGitCmd(projectDir, "checkout", "-b", "hotfix")
        runGitCmd(projectDir, "commit", "-m", "hot fix for issue", "--allow-empty")
        runGitCmd(projectDir, "tag", "-a", "1.0.0-hotfix", "-m", "1.0.0-hotfix")
        String commitId = runGitCmd(projectDir, "rev-parse", "HEAD").trim()

        // switch back to main branch and merge hotfix branch into main branch
        runGitCmd(projectDir, "checkout", master)
        runGitCmd(projectDir, "merge", commitId, "--no-ff", "-m", "merge commit")

        // tag merge commit on main branch as 2.0.0
        runGitCmd(projectDir, "tag", "-a", "2.0.0", "-m", "2.0.0")

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")
        dirtyContentFile << 'dirty-content'

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        //buildResult.output.contains(projectDir.getAbsolutePath())
        // clue: .dirty is tacked on
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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n1.0.0\n0\n[a-z0-9]{10}\n[a-z0-9]{40}\n(master|main)\ntrue\n"
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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "initial commit")
        String sha = runGitCmd(projectDir, "rev-parse", "--short", "HEAD").trim()

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")
        runGitCmd(projectDir, "commit", "-m", "'commit 2'", "--allow-empty")

        when:
        BuildResult buildResult = with('printVersionDetails').build()

        then:
        buildResult.output =~ ":printVersionDetails\n1.0.0\n1\n[a-z0-9]{10}\n(master|main)\nfalse\n"
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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "initial commit")
        String commitId = runGitCmd(projectDir, "rev-parse", "HEAD").trim()
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")
        runGitCmd(projectDir, "commit", "-m", "commit 2", "--allow-empty")
        runGitCmd(projectDir, "checkout", commitId)

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "-a", "my-product@1.0.0", "-m", "my-product@1.0.0")
        runGitCmd(projectDir, "commit", "-m", "'commit 2'", "--allow-empty")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "initial commit")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")
        dirtyContentFile << 'dirty-content'
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "add some stuff")
        String commitSha = runGitCmd(projectDir, "rev-parse", "HEAD").trim()

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "initial commit")
        runGitCmd(projectDir, "tag", "1.0.0")
        dirtyContentFile << 'dirty-content'
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "add some stuff")
        String commitSha = runGitCmd(projectDir, "rev-parse", "HEAD").trim()

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


        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")

        File subDir = Files.createDirectory(temporaryFolder.toPath().resolve('sub')).toFile()

        gitInit(subDir)
        File subDirty = new File(subDir, 'subDirty')
        subDirty.createNewFile()
        runGitCmd(subDir, "add", ".")
        runGitCmd(subDir, "commit", "-m", "'initial commit sub'")
        runGitCmd(subDir, "tag", "-a", "8.8.8", "-m", "8.8")
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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "1.0.0")
        runGitCmd(projectDir, "tag", "-a", "2.0.0", "-m", "2.0.0")
        runGitCmd(projectDir, "tag", "3.0.0")

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        Date d1 = new Date() - 2;
        HashMap<String, String> envvar1 = new HashMap<>();
        envvar1.put("GIT_COMMITTER_DATE", d1.toString())
        runGitCmd(projectDir, envvar1, "-c", "user.name='name'", "-c", "user.email=email@example.com", "tag", "-a", "1.0.0", "-m", "1.0.0")
        Date d2 = new Date();
        HashMap<String, String> envvar2 = new HashMap<>();
        envvar2.put("GIT_COMMITTER_DATE", d2.toString())
        runGitCmd(projectDir, envvar2, "-c", "user.name='name'", "-c", "user.email=email@example.com", "tag", "-a", "2.0.0", "-m", "2.0.0")
        Date d3 = new Date() - 1;
        HashMap<String, String> envvar3 = new HashMap<>();
        envvar3.put("GIT_COMMITTER_DATE", d3.toString())
        runGitCmd(projectDir, envvar3, "-c", "user.name='name'", "-c", "user.email=email@example.com", "tag", "-a", "3.0.0", "-m", "3.0.0")

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "'initial commit'")
        runGitCmd(projectDir, "tag", "2.0.0")
        runGitCmd(projectDir, "tag", "1.0.0")
        runGitCmd(projectDir, "tag", "3.0.0")

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

        gitInit(projectDir)
        runGitCmd(projectDir, "add", ".")
        runGitCmd(projectDir, "commit", "-m", "initial commit")
        runGitCmd(projectDir, "tag", "-a", "1.0.0", "-m", "1.0.0")
        String latestCommit = runGitCmd(projectDir, "rev-parse", "HEAD").trim()

        int depth = 100
        for (int i = 0; i < depth; i++) {
            runGitCmd(projectDir, "add", ".")
            runGitCmd(projectDir, "commit", "-m", "commit-${i}", "--allow-empty")
            latestCommit = runGitCmd(projectDir, "rev-parse", "HEAD").trim()
        }

        when:
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0-${depth}-g${latestCommit.substring(0, 7)}\n")
    }


    private GradleRunner with(String... tasks) {
        return with(Optional.empty(), Optional.empty(), tasks)
    }

    private GradleRunner with(Optional<String> gradleVersion, Optional<Map<String, String>> envVars, String... tasks) {
        List<String> arguments = new ArrayList<>(['--stacktrace'])
        arguments.addAll(tasks)

        def gradleRunner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir)
                .withArguments(arguments)

        gradleVersion.ifPresent({ version -> gradleRunner.withGradleVersion(version) })
        envVars.ifPresent {env ->
            Map<String, String> systemEnv = new HashMap<>(System.getenv())
            systemEnv.putAll(env)
            gradleRunner.withEnvironment(systemEnv)
        }

        return gradleRunner
    }
    
    private static String runGitCmd(File directory, String ...commands) {
        return runGitCmd(directory, [:], commands);
    }


    private static String runGitCmd(File directory, Map<String, String> envvars, String... commands)
            throws IOException, InterruptedException {
        List<String> cmdInput = new ArrayList<>();
        cmdInput.add("git");
        cmdInput.addAll(Arrays.asList(commands));
        ProcessBuilder pb = new ProcessBuilder(cmdInput);
        Map<String, String> environment = pb.environment();
        environment.putAll(envvars);
        pb.directory(directory);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return "";
        }

        return builder.toString().trim();
    };

    private static void gitInit(File projectDir) {
        runGitCmd(projectDir, "init", projectDir.toString())

        // So git doesn't ask you to gpgsign when running tests locally
        runGitCmd(projectDir, "config", "commit.gpgsign", "false")
        runGitCmd(projectDir, "config", "tag.gpgsign", "false")
        runGitCmd(projectDir, "config", "tag.forcesignannotated", "false")

        runGitCmd(projectDir, "config", "user.email", "email@example.com")
        runGitCmd(projectDir, "config", "user.name", "name")
    }
}
