package com.tanman.chattranslator.client.translation;

import com.tanman.chattranslator.ChatTranslator;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Translates chat text using a locally cached MarianMT ONNX model.
 *
 * <p>Models are loaded lazily and cached per model directory (i.e. per language
 * pair), so repeated translations of the same pair do not re-read ~100 MB of
 * ONNX graphs from disk. All failures — missing files, model load errors,
 * inference errors — are reported as {@link TranslationResult#failure} rather
 * than thrown, so callers on the chat path never have to handle exceptions.
 */
public class Translator implements AutoCloseable {

    private final Map<Path, MarianMtModel> models = new ConcurrentHashMap<>();

    /**
     * Guards the native ONNX resources against use-after-free.
     *
     * <p>A translation holds the read lock for its whole duration; {@link #close()}
     * takes the write lock before freeing anything. Releasing an {@code OnnxTensor}
     * or session while another thread is mid-inference is not a catchable Java
     * exception — it can take down the JVM — so a concurrent close must block until
     * in-flight translations finish rather than race them. Read locks are shared, so
     * translations of different language pairs still proceed in parallel.
     */
    private final ReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

    /** Guarded by {@link #lifecycleLock}. */
    private boolean closed;

    /**
     * Translates {@code text} using the model cached in {@code modelDir}.
     *
     * @param text the source text
     * @param modelDir a directory populated by {@link ModelDownloader}
     * @return a successful result, or {@link TranslationResult#failure} if
     *     anything went wrong or the model produced no output
     */
    public TranslationResult translate(String text, Path modelDir) {
        Lock readLock = lifecycleLock.readLock();
        readLock.lock();
        try {
            if (closed) {
                return TranslationResult.failure(text);
            }
            // Normalised so two spellings of the same directory share one loaded model.
            Path key = modelDir.toAbsolutePath().normalize();
            MarianMtModel model = models.computeIfAbsent(key, Translator::loadModel);
            String translated = model.translate(text);
            if (translated == null || translated.isBlank()) {
                return TranslationResult.failure(text);
            }
            return TranslationResult.success(translated, text);
        } catch (Exception e) {
            ChatTranslator.LOGGER.warn("Translation failed using model at {}", modelDir, e);
            return TranslationResult.failure(text);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Wraps the checked-exception load so it can be used from
     * {@code computeIfAbsent}. A failed load leaves no cache entry, so a later
     * call retries rather than caching the failure permanently.
     */
    private static MarianMtModel loadModel(Path modelDir) {
        try {
            return MarianMtModel.load(modelDir);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load translation model at " + modelDir, e);
        }
    }

    /**
     * Releases every loaded model and its native ONNX resources.
     *
     * <p>Blocks until any in-flight translation has finished. Idempotent.
     */
    @Override
    public void close() {
        Lock writeLock = lifecycleLock.writeLock();
        writeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            // Exclusive access here, so no entry can be inserted between iterating and
            // clearing: translate() cannot be running or start until the lock is released.
            for (MarianMtModel model : models.values()) {
                model.close();
            }
            models.clear();
        } finally {
            writeLock.unlock();
        }
    }
}
