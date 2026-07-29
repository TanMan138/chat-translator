package com.tanman.chattranslator.client.translation;

import java.nio.file.Files;
import java.nio.file.Path;

public class ModelManager {

    private final Path baseDir;

    public ModelManager(Path baseDir) {
        this.baseDir = baseDir;
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
}
