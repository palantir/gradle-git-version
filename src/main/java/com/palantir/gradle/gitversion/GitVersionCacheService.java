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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GitVersionCacheService implements BuildService<BuildServiceParameters.None> {

    private static final Logger log = LoggerFactory.getLogger(GitVersionCacheService.class);

    private final Timer timer = new Timer();
    private final ConcurrentMap<GitVersionArgs, VersionDetails> versionDetailsMap = new ConcurrentHashMap<>();

    public final String getGitVersion(File project, Object args) {
        File gitDir = getRootGitDir(project);
        GitVersionArgs gitVersionArgs = GitVersionArgs.fromGroovyClosure(args);
        String gitVersion = versionDetailsMap
                .computeIfAbsent(gitVersionArgs, k -> createVersionDetails(gitDir, k))
                .getVersion();
        return gitVersion;
    }

    public final VersionDetails getVersionDetails(File project, Object args) {
        File gitDir = getRootGitDir(project);
        GitVersionArgs gitVersionArgs = GitVersionArgs.fromGroovyClosure(args);
        VersionDetails versionDetails =
                versionDetailsMap.computeIfAbsent(gitVersionArgs, k -> createVersionDetails(gitDir, k));
        return versionDetails;
    }

    private VersionDetails createVersionDetails(File gitDir, GitVersionArgs args) {
        try {
            return TimingVersionDetails.wrap(timer, new VersionDetailsImpl(gitDir, args));
        } catch (IOException e) {
            log.debug("Cannot compute version details", e);
            return null;
        }
    }

    public final Timer timer() {
        return timer;
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

    public static Provider<GitVersionCacheService> getSharedGitVersionCacheService(Project project) {
        return project.getGradle()
                .getSharedServices()
                .registerIfAbsent("GitVersionCacheService", GitVersionCacheService.class, _spec -> {});
    }
}
