package com.tanman.chattranslator.client.translation.backend;

import com.tanman.chattranslator.client.translation.TranslationResult;

import java.util.concurrent.CompletableFuture;

public interface TranslationBackend {

    /** Whether this backend uses local OPUS-MT model download/cache. */
    boolean requiresModelDownload();

    CompletableFuture<TranslationResult> translate(String text, String sourceLang, String targetLang);
}
