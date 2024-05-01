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

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GitImpl implements Git {

    private static final Logger log = LoggerFactory.getLogger(GitImpl.class);

    private final File directory;
    private final GitVersionArgs args;

    GitImpl(File directory, GitVersionArgs args) {
        this(directory, args, false);
    }

    @VisibleForTesting
    GitImpl(File directory, GitVersionArgs args, boolean testing) {
        if (!gitCommandExists()) {
            throw new RuntimeException("Git not found in project");
        }
        this.directory = directory;
        this.args = args;
        if (testing && !checkIfUserIsSet()) {
            setGitUser();
        }
    }

    @VisibleForTesting
    String runGitCommand(String... commands) throws IOException, InterruptedException {
        return runGitCommand(new HashMap<>(), commands);
    }

    @VisibleForTesting
    String runGitCommand(Map<String, String> envvars, String... commands) throws IOException, InterruptedException {
        List<String> cmdInput = new ArrayList<>();
        cmdInput.add("git");
        cmdInput.addAll(Arrays.asList(commands));
        ProcessBuilder pb = new ProcessBuilder(cmdInput);
        Map<String, String> environment = pb.environment();
        environment.putAll(envvars);
        pb.directory(directory);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        StringBuilder builder = new StringBuilder();
        String line;
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

    private boolean checkIfUserIsSet() {
        try {
            String userEmail = runGitCommand("config", "user.email");
            if (userEmail.isEmpty()) {
                return false;
            }
            return true;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("git config user.email failed", e);
            return false;
        }
    }

    private void setGitUser() {
        try {
            runGitCommand("config", "--global", "user.email", "email@example.com");
            runGitCommand("config", "--global", "user.name", "name");
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("git set user failed", e);
        }
    }

    @Override
    public String getCurrentBranch() {
        try {
            String branch = runGitCommand("branch", "--show-current");
            if (branch.isEmpty()) {
                return null;
            }
            return branch;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("git branch --show-current failed", e);
            return null;
        }
    }

    @Override
    public String getCurrentHeadFullHash() {
        try {
            return runGitCommand("rev-parse", "HEAD");
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("git rev-parse HEAD failed", e);
            return null;
        }
    }

    @Override
    public Boolean isClean() {
        try {
            String result = runGitCommand("status", "--porcelain");
            if (result.isEmpty()) {
                return true;
            }
            return false;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("git status --porcelain failed", e);
            return null;
        }
    }

    @Override
    public String describe() {
        try {
            String result = runGitCommand(
                    "describe",
                    "--tags",
                    "--always",
                    "--first-parent",
                    "--abbrev=7",
                    "--match=" + args.getPrefix() + "*",
                    "HEAD");
            if (result.isEmpty()) {
                return null;
            }
            return result;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("git describe failed", e);
            return null;
        }
    }

    private boolean gitCommandExists() {
        try {
            // verify that "git" command exists (throws exception if it does not)
            Process gitVersionProcess = new ProcessBuilder("git", "version").start();
            if (gitVersionProcess.waitFor() != 0) {
                throw new IllegalStateException("error invoking git command");
            }
            return true;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("git command not found", e);
            return false;
        }
    }
}
