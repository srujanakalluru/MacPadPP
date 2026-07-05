package com.sk.macpad.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Recursive "Find in Files" search over a directory tree. */
public final class FileSearchService {

    private FileSearchService() { }

    private static final Set<String> SKIP_DIRS =
            Set.of("node_modules", ".git", ".svn", "dist", "build", ".cache", "__pycache__");
    private static final long MAX_FILE_SIZE = 2_000_000;
    private static final int MAX_MATCHES = 2000;

    /** One matching line. */
    public record Match(File file, int line, String preview) {
        @Override public String toString() {
            return file.getName() + ":" + line + "   " + preview;
        }
    }

    public static List<Match> search(File root, Pattern pattern) {
        List<Match> matches = new ArrayList<>();
        try {
            Files.walkFileTree(root.toPath(), new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    boolean skip = SKIP_DIRS.contains(name)
                            || (name.startsWith(".") && !dir.equals(root.toPath()));
                    return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.size() > MAX_FILE_SIZE || matches.size() > MAX_MATCHES) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        byte[] bytes = Files.readAllBytes(file);
                        for (int i = 0; i < Math.min(bytes.length, 8192); i++) {
                            if (bytes[i] == 0) return FileVisitResult.CONTINUE;
                        }
                        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
                        for (int i = 0; i < lines.length; i++) {
                            if (pattern.matcher(lines[i]).find()) {
                                matches.add(new Match(file.toFile(), i + 1, lines[i].strip()));
                            }
                        }
                    } catch (IOException ignored) {
                        // ignore
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
        return matches;
    }
}
