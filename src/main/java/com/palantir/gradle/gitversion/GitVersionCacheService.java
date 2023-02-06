/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

public abstract class GitVersionCacheService implements BuildService<BuildServiceParameters.None> {

    private final Timer timer;
    private Map<String, VersionDetails> versionDetailsMap;

    public GitVersionCacheService() {
        timer = new Timer();
        versionDetailsMap = new HashMap<>();
    }

    public final String getGitVersion(String project, Object args) {
        File gitDir = getRootGitDir(new File(project));
        GitVersionArgs gitVersionArgs = GitVersionArgs.fromGroovyClosure(args);
        String key = gitDir.toString() + "|" + gitVersionArgs.getPrefix();
        if (versionDetailsMap.containsKey(key)) {
            return versionDetailsMap.get(key).getVersion();
        }
        Git git = gitRepo(gitDir);
        VersionDetails versionDetails =
                TimingVersionDetails.wrap(timer, new VersionDetailsImpl(git, GitVersionArgs.fromGroovyClosure(args)));
        versionDetailsMap.put(key, versionDetails);
        String gitVersion = versionDetails.getVersion();
        return gitVersion;
    }

    public final VersionDetails getVersionDetails(String project, Object args) {
        File gitDir = getRootGitDir(new File(project));
        GitVersionArgs gitVersionArgs = GitVersionArgs.fromGroovyClosure(args);
        String key = gitDir.toString() + "|" + gitVersionArgs.getPrefix();
        if (versionDetailsMap.containsKey(key)) {
            return versionDetailsMap.get(key);
        }
        Git git = gitRepo(gitDir);
        VersionDetails versionDetails =
                TimingVersionDetails.wrap(timer, new VersionDetailsImpl(git, GitVersionArgs.fromGroovyClosure(args)));
        versionDetailsMap.put(key, versionDetails);
        return versionDetails;
    }

    public final Timer timer() {
        return timer;
    }

    private Git gitRepo(File gitDir) {
        try {
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
