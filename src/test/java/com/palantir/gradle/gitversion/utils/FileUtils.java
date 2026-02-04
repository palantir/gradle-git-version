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

package com.palantir.gradle.gitversion.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public final class FileUtils {
    public static void copyDirectory(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes _attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes _attrs) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path _file, IOException exc) throws IOException {
                throw new IOException("Failed to copy file", exc);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path _dir, IOException exc) throws IOException {
                if (Optional.ofNullable(exc).isPresent()) {
                    throw new UncheckedIOException("Failed to copy directory", exc);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteDirectory(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(FileUtils::deleteFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete directory", e);
        }
    }

    private static void deleteFile(Path targetPath) {
        try {
            Files.delete(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Failed to delete path %s", targetPath), e);
        }
    }

    private FileUtils() {}
}
