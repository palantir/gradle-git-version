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
package com.palantir.gradle.gitversion;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class GitVersionPluginTests {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.settingsGradle().rootProjectName("gradle-test");
        rootProject.file(".gitignore").append(".gradle\n");
        rootProject.file("dirty").createEmpty();
    }

    @Test
    void exception_when_project_root_does_not_have_a_git_repo(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsWithFailure();

        assertThat(buildResult).output().contains("> Cannot find '.git' directory");
    }

    @Test
    void git_describe_works_when_git_repo_is_multiple_levels_up(RootProject rootProject)
            throws IOException, InterruptedException {
        Path rootFolder = rootProject.path();
        Path projectDir = Files.createDirectories(rootFolder.resolve("level1/level2"));
        Path buildFile = projectDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");
        Files.writeString(projectDir.resolve("settings.gradle"), "");

        gitInit(rootFolder.toFile());
        runGitCmd(rootFolder.toFile(), "add", ".");
        runGitCmd(rootFolder.toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootFolder.toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        // will build the project at projectDir
        // Using GradleRunner directly since the framework doesn't support changing working directory
        BuildResult buildResult = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments("printVersion", "--stacktrace")
                .build();

        org.assertj.core.api.Assertions.assertThat(buildResult.getOutput()).contains(":printVersion\n1.0.0\n");
    }

    @Test
    void git_describe_works_when_using_worktree(RootProject rootProject) throws IOException, InterruptedException {
        Path rootFolder = rootProject.path();
        Path projectDir = Files.createDirectories(rootFolder.resolve("worktree"));
        Path originalDir = Files.createDirectories(rootFolder.resolve("original"));
        Path buildFile = originalDir.resolve("build.gradle");
        Files.writeString(buildFile, """
            plugins {
                id 'com.palantir.git-version'
            }
            version gitVersion()
            """);
        Files.writeString(originalDir.resolve("settings.gradle"), "");
        Path originalGitIgnoreFile = originalDir.resolve(".gitignore");
        Files.writeString(originalGitIgnoreFile, ".gradle\n");

        gitInit(originalDir.toFile());
        runGitCmd(originalDir.toFile(), "add", ".");
        runGitCmd(originalDir.toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(originalDir.toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        runGitCmd(originalDir.toFile(), "branch", "newbranch");
        runGitCmd(originalDir.toFile(), "worktree", "add", "../worktree", "newbranch");

        // will build the project at projectDir
        // Using GradleRunner directly since the framework doesn't support changing working directory
        BuildResult buildResult = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(projectDir.toFile())
                .withArguments("printVersion", "--stacktrace")
                .build();

        org.assertj.core.api.Assertions.assertThat(buildResult.getOutput()).contains(":printVersion\n1.0.0\n");
    }

    @Test
    void git_version_can_be_applied_on_sub_modules(GradleInvoker gradle, RootProject rootProject, SubProject submodule)
            throws IOException, InterruptedException {
        submodule.buildGradle().plugins().add("com.palantir.git-version");
        submodule.buildGradle().append("""
            version gitVersion()
            """);

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n1.0.0\n");
    }

    @Test
    void unspecified_when_no_tags_are_present(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);

        gitInit(rootProject.path().toFile());

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\nunspecified\n");
    }

    @Test
    void git_describe_when_annotated_tag_is_present(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");
        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n1.0.0\n");
    }

    @Test
    void git_version_uses_git_version_environment_variable_if_it_is_set(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");
        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        InvocationResult normalResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(normalResult).output().contains(":printVersion\n1.0.0\n");

        // Using GradleRunner directly since the framework doesn't support setting environment variables
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("GIT_VERSION", "999");
        BuildResult overriddenBuildResult = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(rootProject.path().toFile())
                .withArguments("printVersion", "--stacktrace")
                .withEnvironment(env)
                .build();

        org.assertj.core.api.Assertions.assertThat(overriddenBuildResult.getOutput())
                .contains(":printVersion\n999\n");
    }

    @Test
    void git_describe_when_lightweight_tag_is_present(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");
        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "1.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n1.0.0\n");
    }

    @Test
    void git_describe_when_annotated_tag_is_present_with_merge_commit(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        // create repository with a single commit tagged as 1.0.0
        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "initial commit");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"
        String master = runGitCmd(rootProject.path().toFile(), "rev-parse", "--short", "HEAD")
                .trim();
        runGitCmd(rootProject.path().toFile(), "checkout", "-b", "hotfix");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "hot fix for issue", "--allow-empty");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0-hotfix", "-m", "1.0.0-hotfix");
        String commitId =
                runGitCmd(rootProject.path().toFile(), "rev-parse", "HEAD").trim();
        // switch back to main branch and merge hotfix branch into main branch
        runGitCmd(rootProject.path().toFile(), "checkout", master);
        runGitCmd(rootProject.path().toFile(), "merge", commitId, "--no-ff", "-m", "merge commit");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().containsPattern(":printVersion\\n1\\.0\\.0-1-g[a-z0-9]{7}\\n");
    }

    @Test
    void git_describe_when_annotated_tag_is_present_after_merge_commit(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        // create repository with a single commit tagged as 1.0.0

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "initial commit");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"

        String master = runGitCmd(rootProject.path().toFile(), "rev-parse", "--short", "HEAD")
                .trim();
        runGitCmd(rootProject.path().toFile(), "checkout", "-b", "hotfix");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "hot fix for issue", "--allow-empty");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0-hotfix", "-m", "1.0.0-hotfix");
        String commitId =
                runGitCmd(rootProject.path().toFile(), "rev-parse", "HEAD").trim();

        // switch back to main branch and merge hotfix branch into main branch
        runGitCmd(rootProject.path().toFile(), "checkout", master);
        runGitCmd(rootProject.path().toFile(), "merge", commitId, "--no-ff", "-m", "merge commit");

        // tag merge commit on main branch as 2.0.0
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "2.0.0", "-m", "2.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().containsPattern(":printVersion\\n2\\.0\\.0\\n");
    }

    @Test
    void git_describe_and_dirty_when_annotated_tag_is_present_and_dirty_content(
            GradleInvoker gradle, RootProject rootProject) throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        rootProject.file("dirty").append("dirty-content");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        // clue: .dirty is tacked on
        assertThat(buildResult).output().contains(":printVersion\n1.0.0.dirty\n");
    }

    @Test
    void version_details_on_commit_with_a_tag(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            task printVersionDetails { doLast {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().gitHashFull
                println versionDetails().branchName
                println versionDetails().isCleanTag
            }}
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersionDetails").buildsSuccessfully();

        assertThat(buildResult)
                .output()
                .containsPattern(
                        ":printVersionDetails\\n1\\.0\\.0\\n0\\n[a-z0-9]{10}\\n[a-z0-9]{40}\\n(master|main)\\ntrue\\n");
    }

    @Test
    void version_details_can_be_accessed_using_extra_properties_method(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            task printVersionDetails { doLast {
                println project.getExtensions().getExtraProperties().get('versionDetails')().lastTag
                println project.getExtensions().getExtraProperties().get('gitVersion')()
            }}
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "initial commit");
        String sha = runGitCmd(rootProject.path().toFile(), "rev-parse", "--short", "HEAD")
                .trim();

        InvocationResult buildResult = gradle.withArgs("printVersionDetails").buildsSuccessfully();

        assertThat(buildResult).output().containsPattern(":printVersionDetails\\n" + sha + "\\n" + sha + "\\n");
    }

    @Test
    void version_details_when_commit_distance_to_tag_is_greater_than_0(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            task printVersionDetails { doLast {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().branchName
                println versionDetails().isCleanTag
            }}
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'commit 2'", "--allow-empty");

        InvocationResult buildResult = gradle.withArgs("printVersionDetails").buildsSuccessfully();

        assertThat(buildResult)
                .output()
                .containsPattern(":printVersionDetails\\n1\\.0\\.0\\n1\\n[a-z0-9]{10}\\n(master|main)\\nfalse\\n");
    }

    @Test
    void is_clean_tag_should_be_false_when_repo_dirty_on_a_tag_checkout(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            task printVersionDetails { doLast {
                println versionDetails().isCleanTag
            }}
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        rootProject.file("dirty").append("dirty-content");

        InvocationResult buildResult = gradle.withArgs("printVersionDetails").buildsSuccessfully();

        assertThat(buildResult).output().containsPattern(":printVersionDetails\\nfalse\\n");
    }

    @Test
    void version_details_when_detached_head_mode(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            task printVersionDetails { doLast {
                println versionDetails().lastTag
                println versionDetails().commitDistance
                println versionDetails().gitHash
                println versionDetails().branchName
            }}
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "initial commit");
        String commitId =
                runGitCmd(rootProject.path().toFile(), "rev-parse", "HEAD").trim();
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "commit 2", "--allow-empty");
        runGitCmd(rootProject.path().toFile(), "checkout", commitId);

        InvocationResult buildResult = gradle.withArgs("printVersionDetails").buildsSuccessfully();

        assertThat(buildResult)
                .output()
                .containsPattern(":printVersionDetails\\n1\\.0\\.0\\n0\\n[a-z0-9]{10}\\nnull\\n");
    }

    @Test
    void version_filters_out_tags_not_matching_prefix_and_strips_prefix(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion(prefix:"my-product@")
            task printVersionDetails { doLast {
                println versionDetails(prefix:"my-product@").lastTag
            }}
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "my-product@1.0.0", "-m", "my-product@1.0.0");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'commit 2'", "--allow-empty");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersionDetails").buildsSuccessfully();

        assertThat(buildResult).output().containsPattern(":printVersionDetails\\n1\\.0\\.0\\n");
    }

    @Test
    void git_describe_with_commit_after_annotated_tag(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "initial commit");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        rootProject.file("dirty").append("dirty-content");
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "add some stuff");
        String commitSha =
                runGitCmd(rootProject.path().toFile(), "rev-parse", "HEAD").trim();

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n1.0.0-1-g" + commitSha.substring(0, 7) + "\n");
    }

    @Test
    void git_describe_with_commit_after_lightweight_tag(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "initial commit");
        runGitCmd(rootProject.path().toFile(), "tag", "1.0.0");
        rootProject.file("dirty").append("dirty-content");
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "add some stuff");
        String commitSha =
                runGitCmd(rootProject.path().toFile(), "rev-parse", "HEAD").trim();

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n1.0.0-1-g" + commitSha.substring(0, 7) + "\n");
    }

    @Test
    void test_subproject_version(GradleInvoker gradle, RootProject rootProject, SubProject sub)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        // Using apply plugin within subprojects block is valid
        @SuppressWarnings("GradleTestPluginsBlock")
        com.palantir.gradle.testing.files.gradle.GradleFile _unused =
                rootProject.buildGradle().append("""
                    subprojects {
                        apply plugin: 'com.palantir.git-version'
                        version gitVersion()
                    }
                    """);

        rootProject.file(".gitignore").append("build\n");
        rootProject.file(".gitignore").append("sub\n");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        Path subDir = sub.path();

        gitInit(subDir.toFile());
        Path subDirty = subDir.resolve("subDirty");
        Files.createFile(subDirty);
        runGitCmd(subDir.toFile(), "add", ".");
        runGitCmd(subDir.toFile(), "commit", "-m", "'initial commit sub'");
        runGitCmd(subDir.toFile(), "tag", "-a", "8.8.8", "-m", "8.8");

        InvocationResult buildResult =
                gradle.withArgs("printVersion", ":sub:printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n1.0.0\n");
        assertThat(buildResult).output().contains(":sub:printVersion\n8.8.8\n");
    }

    @Test
    void test_multiple_tags_on_same_commit_annotated_tag_is_chosen(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        // Using apply plugin within subprojects block is valid
        @SuppressWarnings("GradleTestPluginsBlock")
        com.palantir.gradle.testing.files.gradle.GradleFile _unused =
                rootProject.buildGradle().append("""
                    subprojects {
                        apply plugin: 'com.palantir.git-version'
                        version gitVersion()
                    }
                    """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "1.0.0");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "2.0.0", "-m", "2.0.0");
        runGitCmd(rootProject.path().toFile(), "tag", "3.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n2.0.0\n");
    }

    @Test
    void test_multiple_tags_on_same_commit_most_recent_annotated_tag(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        // Using apply plugin within subprojects block is valid
        @SuppressWarnings("GradleTestPluginsBlock")
        com.palantir.gradle.testing.files.gradle.GradleFile _unused =
                rootProject.buildGradle().append("""
                    subprojects {
                        apply plugin: 'com.palantir.git-version'
                        version gitVersion()
                    }
                    """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        Instant d1 = Instant.now().minusSeconds(172800); // 2 days ago
        Map<String, String> envvar1 = new HashMap<>();
        envvar1.put("GIT_COMMITTER_DATE", d1.toString());
        runGitCmd(
                rootProject.path().toFile(),
                envvar1,
                "-c",
                "user.name='name'",
                "-c",
                "user.email=email@example.com",
                "tag",
                "-a",
                "1.0.0",
                "-m",
                "1.0.0");
        Instant d2 = Instant.now();
        Map<String, String> envvar2 = new HashMap<>();
        envvar2.put("GIT_COMMITTER_DATE", d2.toString());
        runGitCmd(
                rootProject.path().toFile(),
                envvar2,
                "-c",
                "user.name='name'",
                "-c",
                "user.email=email@example.com",
                "tag",
                "-a",
                "2.0.0",
                "-m",
                "2.0.0");
        Instant d3 = Instant.now().minusSeconds(86400); // 1 day ago
        Map<String, String> envvar3 = new HashMap<>();
        envvar3.put("GIT_COMMITTER_DATE", d3.toString());
        runGitCmd(
                rootProject.path().toFile(),
                envvar3,
                "-c",
                "user.name='name'",
                "-c",
                "user.email=email@example.com",
                "tag",
                "-a",
                "3.0.0",
                "-m",
                "3.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n2.0.0\n");
    }

    @Test
    void test_multiple_tags_on_same_commit_smaller_unannotated_tag_is_chosen(
            GradleInvoker gradle, RootProject rootProject) throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        // Using apply plugin within subprojects block is valid
        @SuppressWarnings("GradleTestPluginsBlock")
        com.palantir.gradle.testing.files.gradle.GradleFile _unused =
                rootProject.buildGradle().append("""
                    subprojects {
                        apply plugin: 'com.palantir.git-version'
                        version gitVersion()
                    }
                    """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        runGitCmd(rootProject.path().toFile(), "tag", "2.0.0");
        runGitCmd(rootProject.path().toFile(), "tag", "1.0.0");
        runGitCmd(rootProject.path().toFile(), "tag", "3.0.0");

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult).output().contains(":printVersion\n1.0.0\n");
    }

    @Test
    void test_tag_set_on_deep_commit(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        gitInit(rootProject.path().toFile());
        runGitCmd(rootProject.path().toFile(), "add", ".");
        runGitCmd(rootProject.path().toFile(), "commit", "-m", "initial commit");
        runGitCmd(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        String latestCommit =
                runGitCmd(rootProject.path().toFile(), "rev-parse", "HEAD").trim();

        int depth = 100;
        for (int i = 0; i < depth; i++) {
            runGitCmd(rootProject.path().toFile(), "add", ".");
            runGitCmd(rootProject.path().toFile(), "commit", "-m", "commit-" + i, "--allow-empty");
            latestCommit =
                    runGitCmd(rootProject.path().toFile(), "rev-parse", "HEAD").trim();
        }

        InvocationResult buildResult = gradle.withArgs("printVersion").buildsSuccessfully();

        assertThat(buildResult)
                .output()
                .contains(":printVersion\n1.0.0-" + depth + "-g" + latestCommit.substring(0, 7) + "\n");
    }

    private static String runGitCmd(java.io.File directory, String... commands)
            throws IOException, InterruptedException {
        return runGitCmd(directory, Map.of(), commands);
    }

    private static String runGitCmd(java.io.File directory, Map<String, String> envvars, String... commands)
            throws IOException, InterruptedException {
        List<String> cmdInput = new ArrayList<>();
        cmdInput.add("git");
        cmdInput.addAll(List.of(commands));
        ProcessBuilder pb = new ProcessBuilder(cmdInput);
        Map<String, String> environment = pb.environment();
        environment.putAll(envvars);
        pb.directory(directory);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return "";
        }

        return builder.toString().trim();
    }

    private static void gitInit(java.io.File projectDir) throws IOException, InterruptedException {
        runGitCmd(projectDir, "init", projectDir.toString());

        // So git doesn't ask you to gpgsign when running tests locally
        runGitCmd(projectDir, "config", "commit.gpgsign", "false");
        runGitCmd(projectDir, "config", "tag.gpgsign", "false");
        runGitCmd(projectDir, "config", "tag.forcesignannotated", "false");

        runGitCmd(projectDir, "config", "user.email", "email@example.com");
        runGitCmd(projectDir, "config", "user.name", "name");
    }
}
