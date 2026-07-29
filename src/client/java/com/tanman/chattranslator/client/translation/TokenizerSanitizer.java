package com.tanman.chattranslator.client.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tanman.chattranslator.ChatTranslator;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Xenova's {@code opus-mt-*} {@code tokenizer.json} files often ship
 * {@code "normalizer": {"type": "Precompiled", "precompiled_charsmap": null}}.
 * The native Hugging Face tokenizers crate panics on that null (not a Java
 * exception — it aborts the JVM). Strip / neutralize it before load.
 *
 * @see <a href="https://github.com/huggingface/tokenizers/issues/1627">tokenizers#1627</a>
 */
final class TokenizerSanitizer {

    private TokenizerSanitizer() {
    }

    static void sanitize(Path tokenizerJson) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(tokenizerJson, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        if (!needsSanitize(root)) {
            return;
        }

        root.add("normalizer", JsonNull.INSTANCE);

        Path temp = tokenizerJson.resolveSibling(tokenizerJson.getFileName() + ".sanitized");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            writer.write(root.toString());
        }
        Files.move(temp, tokenizerJson, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        ChatTranslator.LOGGER.info("Sanitized null Precompiled normalizer in {}", tokenizerJson);
    }

    private static boolean needsSanitize(JsonObject root) {
        JsonElement normalizer = root.get("normalizer");
        if (normalizer == null || !normalizer.isJsonObject()) {
            return false;
        }
        JsonObject obj = normalizer.getAsJsonObject();
        JsonElement type = obj.get("type");
        if (type == null || !type.isJsonPrimitive() || !"Precompiled".equals(type.getAsString())) {
            return false;
        }
        JsonElement charsmap = obj.get("precompiled_charsmap");
        return charsmap == null || charsmap.isJsonNull();
    }
}
