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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.TimeZone;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class VersionDetailsTest {

    @TempDir
    public File temporaryFolder;

    private Git git;

    @SuppressWarnings({"JdkObsolete", "JavaUtilDate"}) // Suppress usage of 'java.util.Date'
    private static final PersonIdent IDENTITY =
            new PersonIdent("name", "email@address", new Date(1234L), TimeZone.getTimeZone("UTC"));

    @BeforeEach
    public void before() throws GitAPIException {
        git = Git.init().setDirectory(temporaryFolder).call();
    }

    @Test
    public void symlinks_should_result_in_clean_git_tree() throws Exception {
        File fileToLinkTo = write(new File(temporaryFolder, "fileToLinkTo"));
        Files.createSymbolicLink(temporaryFolder.toPath().resolve("fileLink"), fileToLinkTo.toPath());

        File folderToLinkTo = new File(temporaryFolder, "folderToLinkTo");
        assertThat(folderToLinkTo.mkdir()).isTrue();
        write(new File(folderToLinkTo, "dummyFile"));
        Files.createSymbolicLink(temporaryFolder.toPath().resolve("folderLink"), folderToLinkTo.toPath());

        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("unused").setName("1.0.0").call();

        assertThat(versionDetails().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    public void short_sha_when_no_annotated_tags_are_present() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit()
                .setAuthor(IDENTITY)
                .setCommitter(IDENTITY)
                .setMessage("initial commit")
                .call();

        assertThat(versionDetails().getVersion()).isEqualTo("6f0c7ed");
    }

    @Test
    public void short_sha_when_no_annotated_tags_are_present_and_dirty_content() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit()
                .setAuthor(IDENTITY)
                .setCommitter(IDENTITY)
                .setMessage("initial commit")
                .call();
        write(new File(temporaryFolder, "foo"));

        assertThat(versionDetails().getVersion()).isEqualTo("6f0c7ed.dirty");
    }

    @Test
    public void git_version_result_is_being_cached() throws Exception {
        write(new File(temporaryFolder, "foo"));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("cached").setName("1.0.0").call();
        VersionDetails versionDetails = versionDetails();
        assertThat(versionDetails.getVersion()).isEqualTo("1.0.0");
        git.tag().setAnnotated(true).setMessage("unused").setName("2.0.0").call();
        assertThat(versionDetails.getVersion()).isEqualTo("1.0.0");
    }

    private File write(File file) throws IOException {
        Files.write(file.toPath(), "content".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private VersionDetails versionDetails() throws IOException {
        File gitDir = new File(temporaryFolder.toString() + "/.git");
        return new VersionDetailsImpl(gitDir, new GitVersionArgs());
    }
}
