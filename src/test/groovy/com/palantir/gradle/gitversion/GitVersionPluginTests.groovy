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
        println "FINLAY GOT HERE 1"

        gitInit(originalDir)
        runGitCmd(originalDir, [:], "add", ".")
        runGitCmd(originalDir, [:], "commit","-m", "'initial commit'")
        runGitCmd(originalDir, [:], "tag", "-a", "1.0.0", "-m", "1.0.0")
        runGitCmd(originalDir, [:], "branch", "newbranch")
        runGitCmd(originalDir, [:], "worktree", "add", "../worktree", "newbranch")

        println "FINLAY GOT HERE 2"

        when:
        // will build the project at projectDir
        println "FINLAY GOT HERE 3"
        BuildResult buildResult = with('printVersion').build()

        then:
        buildResult.output.contains(":printVersion\n1.0.0\n")
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
        runGitCmd(projectDir, [:], "config", "user.email", "email@example.com")
        runGitCmd(projectDir, [:], "config", "user.name", "Name")
        runGitCmd(projectDir, [:], "init") // <-- FIXED HERE
        runGitCmd(projectDir, [:], "config", "commit.gpgsign", "false")
        runGitCmd(projectDir, [:], "config", "tag.gpgsign", "false")
        runGitCmd(projectDir, [:], "config", "tag.forcesignannotated", "false")

        // Ensure at least one commit exists
        File dummy = new File(projectDir, ".dummy")
        dummy.createNewFile()
        runGitCmd(projectDir, [:], "add", ".dummy")
        runGitCmd(projectDir, [:], "commit", "-m", "initial commit")
    }


}
