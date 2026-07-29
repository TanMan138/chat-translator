package com.tanman.chattranslator.client.translation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class ModelManager {

    private final Path baseDir;

    public ModelManager(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path baseDir() {
        return baseDir;
    }

    public String pairKey(String sourceLang, String targetLang) {
        return sourceLang + "-" + targetLang;
    }

    public Path modelDir(String sourceLang, String targetLang) {
        return baseDir.resolve(pairKey(sourceLang, targetLang));
    }

    public boolean isCached(String sourceLang, String targetLang) {
        Path dir = modelDir(sourceLang, targetLang);
        // Every file ModelDownloader writes must be checked here. If a subset were
        // checked, a partially-failed download would look cached, never be retried,
        // and the missing file's absence would surface later as a load failure.
        return Files.exists(dir.resolve(ModelFiles.ENCODER))
                && Files.exists(dir.resolve(ModelFiles.DECODER))
                && Files.exists(dir.resolve(ModelFiles.TOKENIZER))
                && Files.exists(dir.resolve(ModelFiles.GENERATION_CONFIG));
    }

    /**
     * Cached / partial pair directory names under the models folder (e.g. {@code en-ru}).
     */
    public List<String> listPairKeys() {
        if (!Files.isDirectory(baseDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(baseDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Human-readable size of one pair directory, or {@code "?"} on error. */
    public String formatPairSize(String pairKey) {
        if (!isSafePairKey(pairKey)) {
            return "?";
        }
        return formatBytes(directorySize(baseDir.resolve(pairKey)));
    }

    public String formatTotalSize() {
        long total = 0;
        for (String key : listPairKeys()) {
            total += directorySize(baseDir.resolve(key));
        }
        return formatBytes(total);
    }

    /**
     * Deletes one pair directory. {@code pairKey} must look like {@code en-ru}.
     *
     * @return {@code true} if something was removed
     */
    public boolean deletePair(String pairKey) {
        if (!isSafePairKey(pairKey)) {
            return false;
        }
        Path dir = baseDir.resolve(pairKey).normalize();
        if (!dir.startsWith(baseDir.normalize())) {
            return false;
        }
        if (!Files.exists(dir)) {
            return false;
        }
        return deleteRecursive(dir);
    }

    /**
     * Deletes every pair directory under the models folder.
     *
     * @return number of directories removed
     */
    public int deleteAll() {
        int removed = 0;
        for (String key : new ArrayList<>(listPairKeys())) {
            if (deletePair(key)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Resolves a user token from chat into pair keys to delete.
     *
     * <ul>
     *   <li>{@code en-ru} — that pair only</li>
     *   <li>{@code ru} — both {@code en-ru} and {@code ru-en} if present</li>
     *   <li>{@code all} — every pair</li>
     * </ul>
     */
    public List<String> resolveClearTargets(String token) {
        String t = token.toLowerCase(Locale.ROOT).trim();
        if (t.equals("all")) {
            return listPairKeys();
        }
        if (t.contains("-")) {
            return isSafePairKey(t) ? List.of(t) : List.of();
        }
        if (!t.matches("[a-z]{2,3}")) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(2);
        String forward = pairKey("en", t);
        String reverse = pairKey(t, "en");
        if (Files.isDirectory(baseDir.resolve(forward))) {
            keys.add(forward);
        }
        if (Files.isDirectory(baseDir.resolve(reverse))) {
            keys.add(reverse);
        }
        return keys;
    }

    static boolean isSafePairKey(String pairKey) {
        return pairKey != null && pairKey.matches("[a-z]{2,3}-[a-z]{2,3}");
    }

    static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "?";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private static long directorySize(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        final long[] total = {0};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    total[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            return -1;
        }
        return total[0];
    }

    private static boolean deleteRecursive(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
