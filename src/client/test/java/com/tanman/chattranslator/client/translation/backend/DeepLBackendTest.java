package com.tanman.chattranslator.client.translation.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeepLBackendTest {

    @Test
    void freePlanKeysGoToTheFreeHostWhateverTheSettingSays() {
        assertEquals("api-free.deepl.com",
                DeepLBackend.resolveHost("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:fx", false));
        assertEquals("api-free.deepl.com",
                DeepLBackend.resolveHost("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:fx", true));
    }

    @Test
    void keysWithAnyOtherSuffixGoToThePaidHost() {
        assertEquals("api.deepl.com",
                DeepLBackend.resolveHost("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:dp", true));
    }

    @Test
    void aKeyWithNoSuffixFallsBackToTheSetting() {
        assertEquals("api-free.deepl.com",
                DeepLBackend.resolveHost("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", true));
        assertEquals("api.deepl.com",
                DeepLBackend.resolveHost("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", false));
    }
}
