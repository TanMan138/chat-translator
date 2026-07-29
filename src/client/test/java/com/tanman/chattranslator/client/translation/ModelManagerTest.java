package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelManagerTest {

    @Test
    void pairKeyFormatsAsSourceDashTarget() {
        ModelManager manager = new ModelManager(Path.of("unused"));
        assertEquals("fr-en", manager.pairKey("fr", "en"));
        assertEquals("en-fr", manager.pairKey("en", "fr"));
    }

    @Test
    void notCachedWhenDirectoryMissing(@TempDir Path tempDir) {
        ModelManager manager = new ModelManager(tempDir);
        assertFalse(manager.isCached("fr", "en"));
    }

    @Test
    void notCachedWhenFilesIncomplete(@TempDir Path tempDir) throws IOException {
        ModelManager manager = new ModelManager(tempDir);
        Path dir = manager.modelDir("fr", "en");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ModelFiles.ENCODER), "fake");
        Files.writeString(dir.resolve(ModelFiles.DECODER), "fake");
        // tokenizer.json missing

        assertFalse(manager.isCached("fr", "en"));
    }

    @Test
    void notCachedWhenGenerationConfigMissing(@TempDir Path tempDir) throws IOException {
        // A download that fetched the models and tokenizer but failed on
        // generation_config.json must not look cached, or it would never be retried
        // and the per-language-pair token IDs would be unavailable at load time.
        ModelManager manager = new ModelManager(tempDir);
        Path dir = manager.modelDir("fr", "en");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ModelFiles.ENCODER), "fake");
        Files.writeString(dir.resolve(ModelFiles.DECODER), "fake");
        Files.writeString(dir.resolve(ModelFiles.TOKENIZER), "{}");
        // generation_config.json missing

        assertFalse(manager.isCached("fr", "en"));
    }

    @Test
    void cachedWhenAllFilesPresent(@TempDir Path tempDir) throws IOException {
        ModelManager manager = new ModelManager(tempDir);
        Path dir = manager.modelDir("fr", "en");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ModelFiles.ENCODER), "fake");
        Files.writeString(dir.resolve(ModelFiles.DECODER), "fake");
        Files.writeString(dir.resolve(ModelFiles.TOKENIZER), "{}");
        Files.writeString(dir.resolve(ModelFiles.GENERATION_CONFIG), "{}");

        assertTrue(manager.isCached("fr", "en"));
    }

    @Test
    void modelDirIsScopedUnderBaseDirByPairKey(@TempDir Path tempDir) {
        ModelManager manager = new ModelManager(tempDir);
        assertEquals(tempDir.resolve("fr-en"), manager.modelDir("fr", "en"));
    }

    @Test
    void deletePairRemovesDirectory(@TempDir Path tempDir) throws IOException {
        ModelManager manager = new ModelManager(tempDir);
        Path dir = manager.modelDir("en", "ru");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ModelFiles.ENCODER), "fake");
        assertTrue(manager.deletePair("en-ru"));
        assertFalse(Files.exists(dir));
    }

    @Test
    void resolveClearTargetsForLangCode(@TempDir Path tempDir) throws IOException {
        ModelManager manager = new ModelManager(tempDir);
        Files.createDirectories(manager.modelDir("en", "ru"));
        Files.createDirectories(manager.modelDir("ru", "en"));
        Files.createDirectories(manager.modelDir("en", "fr"));

        assertEquals(List.of("en-ru", "ru-en"), manager.resolveClearTargets("ru"));
        assertEquals(List.of("en-fr"), manager.resolveClearTargets("en-fr"));
        assertEquals(3, manager.resolveClearTargets("all").size());
    }

    @Test
    void rejectsPathTraversalPairKeys(@TempDir Path tempDir) {
        ModelManager manager = new ModelManager(tempDir);
        assertFalse(manager.deletePair("../en-ru"));
        assertFalse(ModelManager.isSafePairKey("en/ru"));
    }
}
