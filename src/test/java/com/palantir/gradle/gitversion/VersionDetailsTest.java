package com.palantir.gradle.gitversion;

import org.eclipse.jgit.api.Git;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionDetailsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void symlinks_should_result_in_clean_git_tree() throws Exception {

        File fileToLinkTo = temporaryFolder.newFile("fileToLinkTo");
        Files.write(fileToLinkTo.toPath(), "content".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(temporaryFolder.getRoot().toPath().resolve("fileLink"), fileToLinkTo.toPath());

        File folderToLinkTo = temporaryFolder.newFolder("folderToLinkTo");
        Files.write(folderToLinkTo.toPath().resolve("dummyFile"), "content".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(temporaryFolder.getRoot().toPath().resolve("folderLink"), folderToLinkTo.toPath());

        Git git = Git.init().setDirectory(temporaryFolder.getRoot()).call();
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("unused").setName("1.0.0").call();

        assertThat(new VersionDetails(git, new GitVersionArgs()).getVersion()).isEqualTo("1.0.0");
    }
}
