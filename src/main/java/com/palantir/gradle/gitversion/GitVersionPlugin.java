package com.palantir.gradle.gitversion;

import groovy.lang.Closure;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.File;
import java.io.IOException;

public class GitVersionPlugin implements Plugin<Project> {
    public void apply(final Project project) {
        project.getExtensions().add("gitVersion", new Closure<String>(this, this) {
            public String doCall(Object args) {
                return versionDetails(project, GitVersionArgs.fromGroovyClosure(args)).getVersion();
            }
        });

        project.getExtensions().add("versionDetails", new Closure<VersionDetails>(this, this) {
            public IVersionDetails doCall(Object args) {
                return versionDetails(project, GitVersionArgs.fromGroovyClosure(args));
            }
        });

        Task printVersionTask = project.getTasks().create("printVersion");
        printVersionTask.doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                System.out.println(project.getVersion());
            }
        });
        printVersionTask.setGroup("Versioning");
        printVersionTask.setDescription("Prints the project's configured version to standard out");
    }

    private IVersionDetails versionDetails(Project project, GitVersionArgs args) {
        try {
            File gitDir = GitCli.getRootGitDir(project.getProjectDir());
            Git git = Git.wrap(new FileRepository(gitDir));
            return new FancyVersionDetails(git, args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

