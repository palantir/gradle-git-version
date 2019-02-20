package com.palantir.gradle.gitversion;

import groovy.lang.Closure;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class GitVersionPlugin implements Plugin<Project> {
    private final Map<GitVersionArgs, Map<File, VersionDetails>> cachedVersionDetails = new HashMap<>();

    public void apply(final Project project) {
        // intentionally not using .getExtension() here for back-compat
        project.getExtensions().getExtraProperties().set("gitVersion", new Closure<String>(this, this) {
            public String doCall(Object args) {
                return getVersionDetails(project, GitVersionArgs.fromGroovyClosure(args)).getVersion();
            }
        });

        project.getExtensions().getExtraProperties().set("versionDetails", new Closure<VersionDetails>(this, this) {
            public VersionDetails doCall(Object args) {
                return getVersionDetails(project, GitVersionArgs.fromGroovyClosure(args));
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

    /**
     * Fetch a cached version of VersionDetails.
     */
    private synchronized VersionDetails getVersionDetails(Project project, GitVersionArgs versionArgs) {
        if (!cachedVersionDetails.containsKey(versionArgs)) {
            cachedVersionDetails.put(versionArgs, new HashMap<File, VersionDetails>());
        }
        Map<File, VersionDetails> versionArgCache = cachedVersionDetails.get(versionArgs);
        File rootGitDir = getRootGitDir(project.getProjectDir());

        if (!versionArgCache.containsKey(rootGitDir)) {
            versionArgCache.put(rootGitDir, new VersionDetails(gitRepo(rootGitDir), versionArgs));
        }

        return versionArgCache.get(rootGitDir);
    }

    private static Git gitRepo(File dir) {
        try {
            File gitDir = getRootGitDir(dir);
            return Git.wrap(new FileRepository(gitDir));
        } catch (IOException e) {
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

