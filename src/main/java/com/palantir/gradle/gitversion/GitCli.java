/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.gitversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class GitCli {
    private GitCli() {}

    static void verifyGitCommandExists() throws IOException, InterruptedException {
        Process gitVersionProcess = new ProcessBuilder("git", "version").start();
        if (gitVersionProcess.waitFor() != 0) {
            throw new IllegalStateException("error invoking git command");
        }
    }

    static String runGitCommand(File dir, String... commands) throws IOException, InterruptedException {
        List<String> cmdInput = new ArrayList<>();
        cmdInput.add("git");
        cmdInput.addAll(Arrays.asList(commands));
        ProcessBuilder pb = new ProcessBuilder(cmdInput);
        pb.directory(dir);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return "";
        }

        return builder.toString().trim();
    }

    static File getRootGitDir(File currentRoot) {
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
