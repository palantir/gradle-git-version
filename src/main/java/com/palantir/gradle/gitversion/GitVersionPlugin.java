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

import groovy.lang.Closure;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public final class GitVersionPlugin implements Plugin<Project> {
    private final Timer timer = new Timer();

    @Override
    public void apply(final Project project) {
        project.getRootProject().getPluginManager().apply(GitVersionRootPlugin.class);

        final Git git = gitRepo(project);

        // intentionally not using .getExtension() here for back-compat
        project.getExtensions().getExtraProperties().set("gitVersion", new Closure<String>(this, this) {
            public String doCall(Object args) {
                return TimingVersionDetails.wrap(
                                timer, new VersionDetailsImpl(git, GitVersionArgs.fromGroovyClosure(args)))
                        .getVersion();
            }
        });

        project.getExtensions().getExtraProperties().set("versionDetails", new Closure<VersionDetails>(this, this) {
            public VersionDetails doCall(Object args) {
                return TimingVersionDetails.wrap(
                        timer, new VersionDetailsImpl(git, GitVersionArgs.fromGroovyClosure(args)));
            }
        });

        Task printVersionTask = project.getTasks().create("printVersion");
        printVersionTask.doLast(new Action<Task>() {
            @Override
            @SuppressWarnings("BanSystemOut")
            public void execute(Task _task) {
                System.out.println(project.getVersion());
            }
        });
        printVersionTask.setGroup("Versioning");
        printVersionTask.setDescription("Prints the project's configured version to standard out");
    }

    Timer timer() {
        return timer;
    }

    private Git gitRepo(Project project) {
        try {
            // holds the working data but maybe not the git data (in git submodules)
            File projectDir = getRootGitDir(project.getProjectDir());

            Git git; // return value
            // read .git file/directory to properly identify the repo's git dir
            // fixing https://github.com/palantir/gradle-git-version/issues/111
            // working around [TODO: submit issue to JGit]
            File dotGitFile = new File(projectDir.getPath());
            if (dotGitFile.isFile()) { // this is a submodule
                RepositoryBuilder builder = new RepositoryBuilder();
                builder.setWorkTree(new File(projectDir.getParent()));

                // set Git Dir manually
                String[] pair =
                        new String(Files.readAllBytes(dotGitFile.toPath()), StandardCharsets.UTF_8).split(":", 2);
                if (pair.length != 2) {
                    throw new ParseException(dotGitFile.toString(), 0);
                }
                if (!pair[0].trim().equals(new String("gitdir"))) {
                    throw new ParseException(pair[0], 0);
                }
                if (pair[1].trim().contains("\n")) {
                    throw new ParseException(pair[1], pair[1].indexOf('\n'));
                }
                String gitDirStr = pair[1].trim();

                File gitDirFile;
                if (!new File(gitDirStr).isAbsolute()) {
                    gitDirFile = new File(new File(projectDir.getParent()), gitDirStr);
                } else {
                    gitDirFile = new File(gitDirStr);
                }
                builder.setGitDir(gitDirFile);

                Repository subRepo = builder.build();
                git = Git.wrap(subRepo);
            } else { // this is a superrepo
                git = Git.wrap(new FileRepository(projectDir)); // JGit::wrap works as expected for superrepos
            }

            return git;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static File getRootGitDir(File currentRoot) {
        File gitDir = scanForRootGitDir(currentRoot);
        if (!gitDir.exists()) {
            throw new IllegalArgumentException("Cannot find '.git' directory");
        }
        return gitDir;
    }

    private static File scanForRootGitDir(File currentRoot) {
        File gitDir = new File(currentRoot, ".git");

        if (gitDir.exists()) {
            return gitDir;
        }

        // stop at the root directory, return non-existing File object;
        if (currentRoot.getParentFile() == null) {
            return gitDir;
        }

        // look in parent directory;
        return scanForRootGitDir(currentRoot.getParentFile());
    }
}
