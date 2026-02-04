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

import com.palantir.gradle.gitversion.utils.FileUtils;
import com.palantir.gradle.gitversion.utils.GitUtils;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
    @SuppressWarnings("GradleTestTemporaryFile")
    void exception_when_project_root_does_not_have_a_valid_git_repo(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        Path tmpDir = Files.createTempDirectory("GitVersionPluginTests");
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);

        Path rootProjectFileName = rootProject.path().getParent().toAbsolutePath();
        FileUtils.copyDirectory(rootProject.path().getParent(), tmpDir.resolve(rootProjectFileName.getFileName()));
        FileUtils.deleteDirectory(rootProjectFileName);
        Files.createSymbolicLink(rootProjectFileName, tmpDir.resolve(rootProjectFileName.getFileName()));

        gradle.withArgs("printVersion")
                .buildsWithFailure()
                .assertThat()
                .output()
                .contains("> Cannot find '.git' directory");

        FileUtils.deleteDirectory(rootProjectFileName);
    }

    @Test
    void git_describe_works_when_git_repo_is_multiple_levels_up(
            GradleInvoker gradle, RootProject rootProject, SubProject level1) throws IOException, InterruptedException {
        SubProject level2 = level1.subproject("level2");
        level2.buildGradle().plugins().add("com.palantir.git-version");
        level2.buildGradle().append("""
            version gitVersion()
            """);

        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":level1:level2:printVersion\n1.0.0\n");
    }

    @Test
    void git_describe_works_when_using_worktree(
            GradleInvoker gradle, RootProject _rootProject, SubProject worktree, SubProject original)
            throws IOException, InterruptedException {
        original.buildGradle().plugins().add("com.palantir.git-version");
        original.buildGradle().append("""
            version gitVersion()
            """);
        original.file(".gitignore").append(".gradle\n");

        GitUtils.gitInit(original.path().toFile());
        GitUtils.runCommands(original.path().toFile(), "add", ".");
        GitUtils.runCommands(original.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(original.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        GitUtils.runCommands(original.path().toFile(), "branch", "newbranch");
        GitUtils.runCommands(
                original.path().toFile(), "worktree", "add", worktree.path().toString(), "newbranch");

        gradle.withArgs(":worktree:printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":worktree:printVersion\n1.0.0\n");
    }

    @Test
    void git_version_can_be_applied_on_sub_modules(GradleInvoker gradle, RootProject rootProject, SubProject submodule)
            throws IOException, InterruptedException {
        submodule.buildGradle().plugins().add("com.palantir.git-version");
        submodule.buildGradle().append("""
            version gitVersion()
            """);
        submodule.gradleFile("settings.gradle").append("""
            include 'submodule'
            """);

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":submodule:printVersion\n1.0.0\n");
    }

    @Test
    void unspecified_when_no_tags_are_present(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);

        GitUtils.gitInit(rootProject.path().toFile());

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\nunspecified\n");
    }

    @Test
    void git_describe_when_annotated_tag_is_present(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0\n");
    }

    @Test
    void git_version_uses_git_version_environment_variable_if_it_is_set(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0\n");

        gradle.withArgs("printVersion", "-P__TESTING=true", "-P__TESTING_GIT_VERSION=999")
                .buildsSuccessfully()
                .assertThat()
                .output()
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

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "1.0.0");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0\n");
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
        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "initial commit");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"
        String master = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "--short", "HEAD")
                .trim();
        GitUtils.runCommands(rootProject.path().toFile(), "checkout", "-b", "hotfix");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "hot fix for issue", "--allow-empty");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0-hotfix", "-m", "1.0.0-hotfix");

        String commitId = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "HEAD")
                .trim();
        // switch back to main branch and merge hotfix branch into main branch
        GitUtils.runCommands(rootProject.path().toFile(), "checkout", master);
        GitUtils.runCommands(rootProject.path().toFile(), "merge", commitId, "--no-ff", "-m", "merge commit");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .containsPattern(":printVersion\\n1\\.0\\.0-1-g[a-z0-9]{7}\\n");
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
        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "initial commit");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        // create a new branch called "hotfix" that has a single commit and is tagged with "1.0.0-hotfix"
        String master = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "--short", "HEAD")
                .trim();
        GitUtils.runCommands(rootProject.path().toFile(), "checkout", "-b", "hotfix");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "hot fix for issue", "--allow-empty");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0-hotfix", "-m", "1.0.0-hotfix");
        String commitId = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "HEAD")
                .trim();

        // switch back to main branch and merge hotfix branch into main branch
        GitUtils.runCommands(rootProject.path().toFile(), "checkout", master);
        GitUtils.runCommands(rootProject.path().toFile(), "merge", commitId, "--no-ff", "-m", "merge commit");

        // tag merge commit on main branch as 2.0.0
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "2.0.0", "-m", "2.0.0");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .containsPattern(":printVersion\\n2\\.0\\.0\\n");
    }

    @Test
    void git_describe_and_dirty_when_annotated_tag_is_present_and_dirty_content(
            GradleInvoker gradle, RootProject rootProject) throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        rootProject.file("dirty").append("dirty-content");

        // clue: .dirty is tacked on
        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0.dirty\n");
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

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        gradle.withArgs("printVersionDetails")
                .buildsSuccessfully()
                .assertThat()
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

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "initial commit");
        String sha = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "--short", "HEAD")
                .trim();

        gradle.withArgs("printVersionDetails")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .containsPattern(":printVersionDetails\\n" + sha + "\\n" + sha + "\\n");
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

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'commit 2'", "--allow-empty");

        gradle.withArgs("printVersionDetails")
                .buildsSuccessfully()
                .assertThat()
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

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        rootProject.file("dirty").append("dirty-content");

        gradle.withArgs("printVersionDetails")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .containsPattern(":printVersionDetails\\nfalse\\n");
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

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "initial commit");
        String commitId = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "HEAD")
                .trim();
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "commit 2", "--allow-empty");
        GitUtils.runCommands(rootProject.path().toFile(), "checkout", commitId);

        gradle.withArgs("printVersionDetails")
                .buildsSuccessfully()
                .assertThat()
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

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "my-product@1.0.0", "-m", "my-product@1.0.0");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'commit 2'", "--allow-empty");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        gradle.withArgs("printVersionDetails")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .containsPattern(":printVersionDetails\\n1\\.0\\.0\\n");
    }

    @Test
    void git_describe_with_commit_after_annotated_tag(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "initial commit");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        rootProject.file("dirty").append("dirty-content");
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "add some stuff");
        String commitSha = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "HEAD")
                .trim();

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0-1-g" + commitSha.substring(0, 7) + "\n");
    }

    @Test
    void git_describe_with_commit_after_lightweight_tag(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "initial commit");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "1.0.0");
        rootProject.file("dirty").append("dirty-content");
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "add some stuff");
        String commitSha = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "HEAD")
                .trim();

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0-1-g" + commitSha.substring(0, 7) + "\n");
    }

    @Test
    void test_subproject_version(GradleInvoker gradle, RootProject rootProject, SubProject sub)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);

        sub.buildGradle().plugins().add("com.palantir.git-version");
        sub.buildGradle().append("""
            version gitVersion()
            """);

        rootProject.file(".gitignore").append("build\n");
        rootProject.file(".gitignore").append("sub\n");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");

        Path subDir = sub.path();
        GitUtils.gitInit(subDir.toFile());
        Path subDirty = subDir.resolve("subDirty");
        Files.createFile(subDirty);
        GitUtils.runCommands(subDir.toFile(), "add", ".");
        GitUtils.runCommands(subDir.toFile(), "commit", "-m", "'initial commit sub'");
        GitUtils.runCommands(subDir.toFile(), "tag", "-a", "8.8.8", "-m", "8.8");

        gradle.withArgs("printVersion", ":sub:printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0\n")
                .contains(":sub:printVersion\n8.8.8\n");
    }

    @Test
    void test_multiple_tags_on_same_commit_annotated_tag_is_chosen(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "1.0.0");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "2.0.0", "-m", "2.0.0");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "3.0.0");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n2.0.0\n");
    }

    @Test
    void test_multiple_tags_on_same_commit_most_recent_annotated_tag(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        Instant d1 = Instant.now().minusSeconds(172800); // 2 days ago
        Map<String, String> envvar1 = new HashMap<>();
        envvar1.put("GIT_COMMITTER_DATE", d1.toString());
        GitUtils.runCommands(
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
        GitUtils.runCommands(
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
        GitUtils.runCommands(
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

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n2.0.0\n");
    }

    @Test
    void test_multiple_tags_on_same_commit_smaller_unannotated_tag_is_chosen(
            GradleInvoker gradle, RootProject rootProject) throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "'initial commit'");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "2.0.0");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "1.0.0");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "3.0.0");

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0\n");
    }

    @Test
    void test_tag_set_on_deep_commit(GradleInvoker gradle, RootProject rootProject)
            throws IOException, InterruptedException {
        rootProject.buildGradle().plugins().add("com.palantir.git-version");
        rootProject.buildGradle().append("""
            version gitVersion()
            """);
        rootProject.file(".gitignore").append("build");

        GitUtils.gitInit(rootProject.path().toFile());
        GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
        GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "initial commit");
        GitUtils.runCommands(rootProject.path().toFile(), "tag", "-a", "1.0.0", "-m", "1.0.0");
        String latestCommit = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "HEAD")
                .trim();

        int depth = 100;
        for (int i = 0; i < depth; i++) {
            GitUtils.runCommands(rootProject.path().toFile(), "add", ".");
            GitUtils.runCommands(rootProject.path().toFile(), "commit", "-m", "commit-" + i, "--allow-empty");
            latestCommit = GitUtils.runCommands(rootProject.path().toFile(), "rev-parse", "HEAD")
                    .trim();
        }

        gradle.withArgs("printVersion")
                .buildsSuccessfully()
                .assertThat()
                .output()
                .contains(":printVersion\n1.0.0-" + depth + "-g" + latestCommit.substring(0, 7) + "\n");
    }
}
