package com.palantir.gradle.gitversion;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionDetailsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private Git git;
    private PersonIdent identity = new PersonIdent("name", "email@address",
            new Date(1234L), TimeZone.getTimeZone("UTC"));

    @Before
    public void before() throws GitAPIException {
        git = Git.init().setDirectory(temporaryFolder.getRoot()).call();
    }

    @Test
    public void symlinks_should_result_in_clean_git_tree() throws Exception {

        File fileToLinkTo = temporaryFolder.newFile("fileToLinkTo");
        Files.write(fileToLinkTo.toPath(), "content".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(temporaryFolder.getRoot().toPath().resolve("fileLink"), fileToLinkTo.toPath());

        File folderToLinkTo = temporaryFolder.newFolder("folderToLinkTo");
        Files.write(folderToLinkTo.toPath().resolve("dummyFile"), "content".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(temporaryFolder.getRoot().toPath().resolve("folderLink"), folderToLinkTo.toPath());

        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("unused").setName("1.0.0").call();

        assertThat(versionDetails().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    public void short_sha_when_no_annotated_tags_are_present() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit()
                .setAuthor(identity)
                .setCommitter(identity)
                .setMessage("initial commit")
                .call();

        assertThat(versionDetails().getVersion()).isEqualTo("6f0c7ed");
    }

    private VersionDetails versionDetails() {
        return new VersionDetails(git, new GitVersionArgs());
    }
}
