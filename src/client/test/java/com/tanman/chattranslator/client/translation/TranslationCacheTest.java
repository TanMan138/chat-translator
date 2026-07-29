package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationCacheTest {

    @Test
    void returnsWhatWasStoredForTheSamePair() {
        TranslationCache cache = new TranslationCache();
        cache.put("fr", "en", "bonjour", "hello");

        assertEquals(Optional.of("hello"), cache.get("fr", "en", "bonjour"));
    }

    @Test
    void languagePairIsPartOfTheKey() {
        TranslationCache cache = new TranslationCache();
        cache.put("fr", "en", "bonjour", "hello");

        assertTrue(cache.get("es", "en", "bonjour").isEmpty());
        assertTrue(cache.get("fr", "de", "bonjour").isEmpty());
        assertTrue(cache.get("fr", "en", "bonsoir").isEmpty());
    }

    @Test
    void evictsLeastRecentlyUsedPastTheLimit() {
        TranslationCache cache = new TranslationCache(2);
        cache.put("fr", "en", "one", "1");
        cache.put("fr", "en", "two", "2");

        // Touch "one" so "two" becomes the eldest.
        assertEquals(Optional.of("1"), cache.get("fr", "en", "one"));
        cache.put("fr", "en", "three", "3");

        assertEquals(2, cache.size());
        assertEquals(Optional.of("1"), cache.get("fr", "en", "one"));
        assertEquals(Optional.of("3"), cache.get("fr", "en", "three"));
        assertTrue(cache.get("fr", "en", "two").isEmpty());
    }

    @Test
    void skipsBlankAndOversizedText() {
        TranslationCache cache = new TranslationCache();
        cache.put("fr", "en", "  ", "hello");
        cache.put("fr", "en", "bonjour", "  ");
        cache.put("fr", "en", "x".repeat(513), "hello");

        assertEquals(0, cache.size());
    }

    @Test
    void clearDropsEverything() {
        TranslationCache cache = new TranslationCache();
        cache.put("fr", "en", "bonjour", "hello");
        cache.clear();

        assertEquals(0, cache.size());
        assertTrue(cache.get("fr", "en", "bonjour").isEmpty());
    }
}
