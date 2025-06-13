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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.gitversion.GradleAwareGit.GitCommandFailed;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class VersionDetailsTest {

    @TempDir
    public File temporaryFolder;

    private Project project;
    private GradleAwareGit gradleAwareGit;

    final String formattedTime = "'2005-04-07T22:13:13'";

    @BeforeEach
    public void before() {
        this.project = ProjectBuilder.builder().withProjectDir(temporaryFolder).build();
        createGitIgnore(temporaryFolder);
        this.gradleAwareGit = new GradleAwareGit(temporaryFolder, project.getProviders());
        try {
            gradleAwareGit.run("init", temporaryFolder.toString());
            gradleAwareGit.run("config", "commit.gpgsign", "false");
            gradleAwareGit.run("config", "tag.gpgsign", "false");
            gradleAwareGit.run("config", "tag.forcesignannotated", "false");
            gradleAwareGit.run("config", "user.email", "email@example.com");
            gradleAwareGit.run("config", "user.name", "name");
        } catch (GitCommandFailed e) {
            throw new RuntimeException(e);
        }
    }

    private void createGitIgnore(File dir) {
        File gitignoreFile = new File(dir, ".gitignore");

        String gitignoreContent = "# Gradle project files\n" + ".gradle/\n"
                + "build/\n"
                + "out/\n"
                + "classes/\n"
                + "\n"
                + "# Gradle wrapper\n"
                + "gradle/\n"
                + "gradlew\n"
                + "gradlew.bat\n"
                + "\n"
                + "# Gradle cache\n"
                + ".gradle/\n"
                + "caches/\n"
                + "\n"
                + "# Local configuration\n"
                + "local.properties\n"
                + "\n"
                + "# Logs\n"
                + "*.log\n";

        try {
            Files.write(gitignoreFile.toPath(), gitignoreContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void symlinks_should_result_in_clean_git_tree() throws Exception {
        File fileToLinkTo = write(new File(temporaryFolder, "fileToLinkTo"));
        Files.createSymbolicLink(temporaryFolder.toPath().resolve("fileLink"), fileToLinkTo.toPath());

        File folderToLinkTo = new File(temporaryFolder, "folderToLinkTo");
        assertThat(folderToLinkTo.mkdir()).isTrue();
        write(new File(folderToLinkTo, "dummyFile"));
        Files.createSymbolicLink(temporaryFolder.toPath().resolve("folderLink"), folderToLinkTo.toPath());

        gradleAwareGit.run("add", ".");
        gradleAwareGit.run("commit", "-m", "'initial commit'");
        gradleAwareGit.run("tag", "-a", "1.0.0", "-m", "unused");

        assertThat(versionDetails().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    public void short_sha_when_no_annotated_tags_are_present() throws Exception {
        gradleAwareGit.run("add", ".");

        Map<String, String> envvar = new HashMap<>();
        envvar.put("GIT_COMMITTER_DATE", formattedTime);
        envvar.put("TZ", "UTC");
        gradleAwareGit.run(
                envvar,
                "-c",
                "user.name='name'",
                "-c",
                "user.email=email@address",
                "commit",
                "--author='name <email@address>'",
                "-m",
                "'initial commit'",
                "--date=" + formattedTime,
                "--allow-empty");

        assertThat(versionDetails().getVersion()).isEqualTo("f2bc772");
    }

    @Test
    public void short_sha_when_no_annotated_tags_are_present_and_dirty_content() throws Exception {
        gradleAwareGit.run("add", ".");

        Map<String, String> envvar = new HashMap<>();
        envvar.put("GIT_COMMITTER_DATE", formattedTime);
        envvar.put("TZ", "UTC");
        gradleAwareGit.run(
                envvar,
                "-c",
                "user.name='name'",
                "-c",
                "user.email=email@address",
                "commit",
                "--author='name <email@address>'",
                "-m",
                "'initial commit'",
                "--date=" + formattedTime,
                "--allow-empty");

        write(new File(temporaryFolder, "foo"));

        assertThat(versionDetails().getVersion()).isEqualTo("f2bc772.dirty");
    }

    @Test
    public void git_version_result_is_being_cached() throws Exception {
        write(new File(temporaryFolder, "foo"));
        gradleAwareGit.run("add", ".");
        gradleAwareGit.run("commit", "-m", "initial commit");
        gradleAwareGit.run("tag", "-a", "1.0.0", "-m", "cached");
        VersionDetails versionDetails = versionDetails();
        assertThat(versionDetails.getVersion()).isEqualTo("1.0.0");
        gradleAwareGit.run("tag", "-a", "2.0.0", "-m", "unused");
        assertThat(versionDetails.getVersion()).isEqualTo("1.0.0");
    }

    private File write(File file) throws IOException {
        Files.write(file.toPath(), "content".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private VersionDetails versionDetails() {
        String gitDir = temporaryFolder.toString() + "/.git";
        return new VersionDetailsImpl(this.project.getProviders(), new File(gitDir), new GitVersionArgs());
    }
}
