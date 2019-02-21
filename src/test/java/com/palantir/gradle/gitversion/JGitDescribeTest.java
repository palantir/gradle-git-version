package com.palantir.gradle.gitversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JGitDescribeTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File projectDir;
    private Git git;
    private PersonIdent identity = new PersonIdent("name", "email@address");

    @Before
    public void before() throws GitAPIException {
        projectDir = temporaryFolder.getRoot();
        git = Git.init().setDirectory(projectDir).call();
    }

    @Test
    public void test_on_annotated_tag() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("1.0.0").setName("1.0.0").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_on_annotated_tag_2() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("1.0.1").setName("1.0.1").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribe());
    }

    @Test
    public void test_on_lightweight_tag() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(false).setName("1.0.0").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_annotated_tag_with_merge_commit() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("1.0.0").setName("1.0.0").call();
        String master = git.getRepository().getFullBranch();
        Ref hotfixBranch = git.branchCreate().setName("hotfix").call();
        git.checkout().setName(hotfixBranch.getName()).call();
        git.commit().setMessage("hot fix for issue").call();
        git.tag().setAnnotated(true).setMessage("1.0.0-hotfix").setName("1.0.0-hotfix").call();
        git.checkout().setName(master).call();
        git.merge().include(git.getRepository().getRef("hotfix"))
                .setFastForward(MergeCommand.FastForwardMode.NO_FF).setMessage("merge commit").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_annotated_tag_after_merge_commit() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("1.0.0").setName("1.0.0").call();
        String master = git.getRepository().getFullBranch();
        Ref hotfixBranch = git.branchCreate().setName("hotfix").call();
        git.checkout().setName(hotfixBranch.getName()).call();
        git.commit().setMessage("hot fix for issue").call();
        git.tag().setAnnotated(true).setMessage("1.0.0-hotfix").setName("1.0.0-hotfix").call();
        git.checkout().setName(master).call();
        git.merge().include(git.getRepository().getRef("hotfix"))
                .setFastForward(MergeCommand.FastForwardMode.NO_FF).setMessage("merge commit").call();
        git.tag().setAnnotated(true).setMessage("2.0.0").setName("2.0.0").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_head_detached() throws Exception {
        git.add().addFilepattern(".").call();
        RevCommit commit1 = git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("1.0.0").setName("1.0.0").call();
        git.commit().setMessage("commit 2").call();
        git.checkout().setName(commit1.getId().getName()).call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_commit_after_annotated_tag() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("1.0.0").setName("1.0.0").call();
        git.add().addFilepattern(".").call();
        git.commit().setMessage("added some stuff").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_multiple_commits_after_annotated_tag() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setMessage("1.0.0").setName("1.0.0").call();
        for (int i = 0; i < 100; i++) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("commit-" + i).call();
        }

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_commit_after_lightweight_tag() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(false).setName("1.0.0").call();
        git.add().addFilepattern(".").call();
        git.commit().setMessage("added some stuff").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_multiple_commits_after_lightweight_tag() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(false).setName("1.0.0").call();
        for (int i = 0; i < 100; i++) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("commit-" + i).call();
        }

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_multiple_tags_annotated_is_chosen() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(false).setName("1.0.0").call();
        git.tag().setAnnotated(true).setName("2.0.0").call();
        git.tag().setAnnotated(false).setName("3.0.0").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_multiple_annotated_tags_most_recent_is_chosen() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(true).setTagger(
                new PersonIdent(identity, new Date(0, 0, 0))).setName("1.0.0").call();
        git.tag().setAnnotated(true).setTagger(
                new PersonIdent(identity, new Date(0, 0, 10))).setName("2.0.0").call();
        git.tag().setAnnotated(true).setTagger(
                new PersonIdent(identity, new Date(0, 0, 5))).setName("3.0.0").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    @Test
    public void test_multiple_lightweight_tags_smaller_is_chosen() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();
        git.tag().setAnnotated(false).setName("2.0.0").call();
        git.tag().setAnnotated(false).setName("1.0.0").call();
        git.tag().setAnnotated(false).setName("3.0.0").call();

        assertThat(jgitDescribe()).isEqualTo(nativeGitDescribe());
        assertThat(jgitDescribe(ReleasingModel.RELEASE_BRANCH)).isEqualTo(nativeGitDescribeLong());
    }

    private String jgitDescribe() throws GitAPIException {
        return new JGitDescribe(git, ReleasingModel.DEVELOP).describe("");
    }

    private String jgitDescribe(ReleasingModel model) throws GitAPIException {
        return new JGitDescribe(git, model).describe("");
    }

    private String nativeGitDescribe() throws IOException, InterruptedException, RuntimeException {
        return runGitCmd(projectDir, "describe", "--tags", "--always", "--first-parent", "HEAD");
    }

    private String nativeGitDescribeLong() throws IOException, InterruptedException, RuntimeException {
        return runGitCmd(projectDir, "describe", "--tags", "--always", "--long", "--first-parent", "HEAD");
    }

    private String runGitCmd(File directory, String... commands)
            throws IOException, InterruptedException, RuntimeException {
        List<String> cmdInput = new ArrayList<>();
        cmdInput.add("git");
        cmdInput.addAll(Arrays.asList(commands));
        ProcessBuilder pb = new ProcessBuilder(cmdInput);
        pb.directory(directory);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(String.format("running git commands '%s' failed with exit code %s",
                    cmdInput, exitCode));
        }

        return builder.toString().trim();
    }
}
