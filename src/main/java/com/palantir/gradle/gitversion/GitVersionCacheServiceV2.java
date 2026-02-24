/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.gradle.gitversion.GitVersionCacheServiceV2.Parameters;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.Nested;

@SuppressWarnings("RedundantModifier")
abstract class GitVersionCacheServiceV2 implements BuildService<Parameters> {

    @Inject
    public GitVersionCacheServiceV2() {}

    interface Parameters extends BuildServiceParameters {
        @Nested
        Property<Git.Factory> getGitFactory();
    }

    private final LoadingCache<Key, Provider<GitExecOutput>> gitInvocationCache =
            Caffeine.newBuilder().build(this::getInvocationUncached);

    Provider<GitExecOutput> invokeWithResult(ProjectLayout projectLayout, String... command) {
        Path rootGitDirectory =
                getRootGitDir(projectLayout.getProjectDirectory().getAsFile()).toPath();
        Key cacheKey = new Key(rootGitDirectory, List.of(command));
        return gitInvocationCache.get(cacheKey);
    }

    private Provider<GitExecOutput> getInvocationUncached(Key cacheKey) {
        File projectDir = cacheKey.dotGitDirectory().getParent().toFile();
        Git git = getParameters().getGitFactory().get().create(projectDir);
        return git.runWithResult(parameters -> parameters.getCommand().set(cacheKey.command()));
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

    private record Key(Path dotGitDirectory, List<String> command) {}
}
