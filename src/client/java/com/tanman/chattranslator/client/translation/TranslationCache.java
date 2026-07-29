package com.tanman.chattranslator.client.translation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded LRU of already-translated lines, keyed by source/target/text.
 *
 * <p>Chat repeats itself constantly ("gg", "bien joué", server MOTDs). Without this
 * every repeat costs another ONNX inference or — on DeepL/Google/Langbly — another
 * billed API character. Pure logic, no client classes, so it is unit testable.
 */
public final class TranslationCache {

    public static final int DEFAULT_MAX_ENTRIES = 500;

    /**
     * Long lines are near-unique, so caching them buys nothing and only holds memory.
     */
    private static final int MAX_CACHEABLE_LENGTH = 512;

    private final Map<String, String> entries;

    public TranslationCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public TranslationCache(int maxEntries) {
        int limit = Math.max(1, maxEntries);
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > limit;
            }
        };
    }

    public synchronized Optional<String> get(String sourceLang, String targetLang, String text) {
        if (!isCacheable(text)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(key(sourceLang, targetLang, text)));
    }

    public synchronized void put(
            String sourceLang, String targetLang, String text, String translated) {
        if (!isCacheable(text) || translated == null || translated.isBlank()) {
            return;
        }
        entries.put(key(sourceLang, targetLang, text), translated);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    static boolean isCacheable(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_CACHEABLE_LENGTH;
    }

    private static String key(String sourceLang, String targetLang, String text) {
        return sourceLang + '|' + targetLang + '|' + text;
    }
}
