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
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mimics git describe by using rev-list to support versions of git < 1.8.4.
 */
class NativeGitImpl implements NativeGit {
    private static final Logger log = LoggerFactory.getLogger(NativeGitImpl.class);

    private static final Splitter LINE_SPLITTER =
            Splitter.on(System.getProperty("line.separator")).omitEmptyStrings();
    private static final Splitter WORD_SPLITTER = Splitter.on(" ").omitEmptyStrings();

    private final File directory;

    NativeGitImpl(File directory) {
        this.directory = directory;
    }

    @VisibleForTesting
    NativeGitImpl(File directory, boolean testing) {
        this.directory = directory;
        if (testing && !checkIfUserIsSet()) {
            setGitUser();
        }
    }

    private String runGitCmd(String... commands) throws IOException, InterruptedException {
        return runGitCmd(new HashMap<String, String>(), commands);
    }

    private String runGitCmd(Map<String, String> envvars, String... commands) throws IOException, InterruptedException {
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
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return "";
            // return Integer.toString(exitCode);
        }

        return builder.toString().trim();
    }

    public String runGitCommand(Map<String, String> envvar, String... command) {
        if (!gitCommandExists()) {
            return null;
        }
        try {
            return runGitCmd(envvar, command);
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git command {} failed.\n", command, e);
            return null;
        }
    }

    public String runGitCommand(String... command) {
        return runGitCommand(new HashMap<>(), command);
    }

    private boolean checkIfUserIsSet() {
        if (!gitCommandExists()) {
            return false;
        }
        try {
            String userEmail = runGitCmd("config", "user.email");
            if (userEmail.isEmpty()) {
                return false;
            }
            return true;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git config user.email failed", e);
            return false;
        }
    }

    private void setGitUser() {
        try {
            runGitCommand("config", "--global", "user.email", "email@example.com");
            runGitCommand("config", "--global", "user.name", "name");
        } catch (RuntimeException e) {
            log.debug("Native git set user failed", e);
        }
    }

    @Override
    public String getCurrentBranch() {
        if (!gitCommandExists()) {
            return null;
        }
        try {
            String branch = runGitCmd("branch", "--show-current");
            if (branch.isEmpty()) {
                return null;
            }
            return branch;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git branch --show-current failed", e);
            return null;
        }
    }

    @Override
    public String getCurrentHeadFullHash() {
        if (!gitCommandExists()) {
            return null;
        }
        try {
            return runGitCmd("rev-parse", "HEAD");
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git branch --show-current failed", e);
            return null;
        }
    }

    public String getStatusOutput() {
        if (!gitCommandExists()) {
            return null;
        }
        try {
            String result = runGitCmd("-C", directory.getAbsolutePath(), "status", "--porcelain");
            return result;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git status --porcelain failed", e);
            return null;
        }
    }

    @Override
    public Boolean isClean() {
        if (!gitCommandExists()) {
            return null;
        }
        try {
            String result = runGitCmd("status", "--porcelain");
            if (result.isEmpty()) {
                return true;
            }
            return false;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git status --porcelain failed", e);
            return null;
        }
    }

    @Override
    public String describe(String prefix) {
        if (!gitCommandExists()) {
            return null;
        }

        try {
            // Get SHAs of all tags, we only need to search for these later on
            Set<String> tagRefs = new HashSet<>();
            for (String tag : LINE_SPLITTER.splitToList(runGitCmd("show-ref", "--tags", "-d"))) {
                List<String> parts = WORD_SPLITTER.splitToList(tag);
                Preconditions.checkArgument(parts.size() == 2, "Could not parse output of `git show-ref`: %s", parts);
                tagRefs.add(parts.get(0));
            }

            List<String> revs = LINE_SPLITTER.splitToList(runGitCmd("rev-list", "--first-parent", "HEAD"));
            for (int depth = 0; depth < revs.size(); depth++) {
                String rev = revs.get(depth);
                if (tagRefs.contains(rev)) {
                    String exactTag = runGitCmd("describe", "--tags", "--exact-match", "--match=" + prefix + "*", rev);
                    if (!exactTag.isEmpty()) {
                        return depth == 0
                                ? exactTag
                                : String.format("%s-%s-g%s", exactTag, depth, GitUtils.abbrevHash(revs.get(0)));
                    }
                }
            }

            // No tags found, so return commit hash of HEAD
            return GitUtils.abbrevHash(runGitCmd("rev-parse", "HEAD"));
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git describe failed", e);
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
            log.debug("Native git command not found", e);
            return false;
        }
    }
}
