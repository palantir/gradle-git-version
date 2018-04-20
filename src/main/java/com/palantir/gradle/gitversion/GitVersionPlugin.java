package com.palantir.gradle.gitversion;

import groovy.lang.Closure;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.File;
import java.io.IOException;

public class GitVersionPlugin implements Plugin<Project> {
    public void apply(final Project project) {

        // intentionally not using .getExtension() here for back-compat
        project.getExtensions().getExtraProperties().set("gitVersion", new Closure<String>(this, this) {
            public String doCall(Object args) {
                return versionDetails(project, GitVersionArgs.fromGroovyClosure(args)).getVersion();
            }
        });

        project.getExtensions().getExtraProperties().set("versionDetails", new Closure<VersionDetails>(this, this) {
            public VersionDetails doCall(Object args) {
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

    public static void verifyPrefix(final String prefix) {
        assert prefix != null && (prefix.equals("") || prefix.matches(PREFIX_REGEX)) : "Specified prefix `" + prefix + "` does not match the allowed format regex `" + PREFIX_REGEX + "`.";
    }

    private static String stripPrefix(String description, final String prefix) {
        return description == null ? description : description.replaceFirst("^" + prefix, "");
    }

    private VersionDetails versionDetails(Project project, GitVersionArgs args) {
        verifyPrefix(args.getPrefix());
        Git git = gitRepo(project);
        String description = stripPrefix(gitDescribe(project, args.getPrefix()), args.getPrefix());
        String hash = gitHash(git);
        String fullHash = gitHashFull(git);
        String branchName = gitBranchName(git);
        boolean isClean = isClean(git);

        return new VersionDetails(description, hash, fullHash, branchName, isClean);
    }

    private Git gitRepo(Project project) {
        try {
            File gitDir = GitCli.getRootGitDir(project.getProjectDir());
            return Git.wrap(new FileRepository(gitDir));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String gitDescribe(Project project, String prefix) {
        // This used to be implemented with JGit and replaced with shelling out to installed git (#46) because JGit
        // didn't support required behavior. Using installed git doesn't work in some environments or
        // with older versions of git client. We're switching back to implementation with JGit. To make sure we don't
        // make breaking change, we're keeping both implementations. Plan is to get rid of installed git implementation.
        // TODO(mbakovic): Use JGit only implementation #87

        Git git = gitRepo(project);
        String nativeGitDescribe = new NativeGitDescribe(project.getProjectDir(), git).describe(prefix);
        String jgitDescribe = new JGitDescribe(git).describe(prefix);

        // If native failed, return JGit one
        if (nativeGitDescribe == null) {
            return jgitDescribe;
        }


        // If native succeeded, make sure it's same as JGit one
        if (!nativeGitDescribe.equals(jgitDescribe)) {
            throw new IllegalStateException(String.format("Inconsistent git describe: native was %s and jgit was %s. " + "Please report this on github.com/palantir/gradle-git-version", nativeGitDescribe, jgitDescribe));
        }


        return jgitDescribe;
    }

    private String gitHash(Git git) {
        String gitHashFull = gitHashFull(git);
        if (gitHashFull == null) {
            return null;
        }

        return gitHashFull.substring(0, VERSION_ABBR_LENGTH);
    }

    private String gitHashFull(Git git) {
        try {
            ObjectId objectId = git.getRepository().findRef(Constants.HEAD).getObjectId();
            if (objectId == null) {
                return null;
            }

            return objectId.name();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String gitBranchName(Git git) {
        try {
            Ref ref = git.getRepository().findRef(git.getRepository().getBranch());
            if (ref == null) {
                return null;
            }

            return ref.getName().substring(Constants.R_HEADS.length());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isClean(Git git) {
        try {
            return git.status().call().isClean();
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    private static final int VERSION_ABBR_LENGTH = 10;
    private static final String PREFIX_REGEX = "[/@]?([A-Za-z]+[/@-])+";
}

