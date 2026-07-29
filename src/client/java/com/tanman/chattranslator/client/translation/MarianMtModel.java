package com.tanman.chattranslator.client.translation;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.NoopTranslator;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One loaded MarianMT translation model (a single language pair), plus the
 * greedy decoding loop that drives it.
 *
 * <p><b>Why there is a hand-written generation loop here.</b> DJL 0.36.0 ships
 * pre-built Hugging Face translators only for encoder-only tasks
 * (classification, embedding, QA, fill-mask, ...) — there is no seq2seq /
 * text2text / MarianMT translator in {@code ai.djl.huggingface.translator}, and
 * {@code ai.djl.modality.nlp.generate} targets decoder-only causal LMs, not
 * encoder-decoder models. The {@code Xenova/opus-mt-*} ONNX export is also split
 * into separate encoder and decoder graphs with no fused generation, so a single
 * {@code Predictor.predict()} call cannot produce a translation. Encoding,
 * autoregressive decoding and detokenisation are therefore driven explicitly.
 *
 * <p>Each ONNX graph is wrapped in its own {@link Predictor} using
 * {@link NoopTranslator}, which passes {@link NDList}s straight through. Tensors
 * are bound to graph inputs <i>by name</i> ({@link NDArray#setName}) rather than
 * positionally, so the code does not depend on ONNX input ordering.
 *
 * <p>Not thread-safe: DJL {@code Predictor}s are not safe for concurrent use, so
 * {@link Translator} serialises calls to {@link #translate}.
 */
final class MarianMtModel implements AutoCloseable {

    /** Hard ceiling on generated tokens; chat messages are short and this bounds a runaway loop. */
    private static final int MAX_NEW_TOKENS = 256;

    /** Hard ceiling on input tokens, matching the Marian configs' {@code max_length}. */
    private static final int MAX_INPUT_TOKENS = 512;

    private final HuggingFaceTokenizer tokenizer;
    private final ZooModel<NDList, NDList> encoderModel;
    private final ZooModel<NDList, NDList> decoderModel;
    private final Predictor<NDList, NDList> encoder;
    private final Predictor<NDList, NDList> decoder;
    private final long decoderStartTokenId;
    private final long eosTokenId;

    private MarianMtModel(
            HuggingFaceTokenizer tokenizer,
            ZooModel<NDList, NDList> encoderModel,
            ZooModel<NDList, NDList> decoderModel,
            Predictor<NDList, NDList> encoder,
            Predictor<NDList, NDList> decoder,
            long decoderStartTokenId,
            long eosTokenId) {
        this.tokenizer = tokenizer;
        this.encoderModel = encoderModel;
        this.decoderModel = decoderModel;
        this.encoder = encoder;
        this.decoder = decoder;
        this.decoderStartTokenId = decoderStartTokenId;
        this.eosTokenId = eosTokenId;
    }

    /**
     * Loads the tokenizer and both ONNX graphs from a cached model directory.
     *
     * @param modelDir directory holding the files named in {@link ModelFiles}
     * @return the loaded model, owning all native resources until {@link #close()}
     * @throws Exception if any file is missing or fails to load
     */
    static MarianMtModel load(Path modelDir) throws Exception {
        // Anything successfully opened before a later failure must still be released,
        // otherwise a partial load leaks native ONNX sessions.
        Deque<AutoCloseable> opened = new ArrayDeque<>();
        try {
            // Xenova ships precompiled_charsmap: null — native tokenizers aborts the JVM
            // on that. Must sanitize before HuggingFaceTokenizer.newInstance.
            TokenizerSanitizer.sanitize(modelDir.resolve(ModelFiles.TOKENIZER));

            HuggingFaceTokenizer tokenizer =
                    HuggingFaceTokenizer.newInstance(modelDir.resolve(ModelFiles.TOKENIZER));
            opened.push(tokenizer);

            ZooModel<NDList, NDList> encoderModel = loadGraph(modelDir.resolve(ModelFiles.ENCODER));
            opened.push(encoderModel);

            ZooModel<NDList, NDList> decoderModel = loadGraph(modelDir.resolve(ModelFiles.DECODER));
            opened.push(decoderModel);

            // Created here rather than in the constructor so that a failure creating the
            // second predictor still releases the first one.
            Predictor<NDList, NDList> encoder = encoderModel.newPredictor();
            opened.push(encoder);
            Predictor<NDList, NDList> decoder = decoderModel.newPredictor();
            opened.push(decoder);

            // These token IDs differ per language pair. Falling back to hardcoded defaults
            // when the file is absent would silently emit garbage for every pair except
            // fr-en, so a missing or malformed config is a hard failure instead.
            String config = readGenerationConfig(modelDir.resolve(ModelFiles.GENERATION_CONFIG));
            long startId = requireLong(config, "decoder_start_token_id", modelDir);
            long eosId = requireLong(config, "eos_token_id", modelDir);

            MarianMtModel model =
                    new MarianMtModel(
                            tokenizer, encoderModel, decoderModel, encoder, decoder, startId, eosId);
            opened.clear(); // ownership transferred to the model
            return model;
        } finally {
            while (!opened.isEmpty()) {
                closeQuietly(opened.pop());
            }
        }
    }

    private static ZooModel<NDList, NDList> loadGraph(Path onnxFile) throws Exception {
        if (!Files.isRegularFile(onnxFile)) {
            throw new IOException("Missing ONNX graph: " + onnxFile);
        }
        // optModelPath accepts a file (not just a directory); OrtModel resolves it directly,
        // which avoids ambiguity between the two .onnx files sharing this directory.
        Criteria<NDList, NDList> criteria =
                Criteria.builder()
                        .setTypes(NDList.class, NDList.class)
                        .optModelPath(onnxFile)
                        .optEngine("OnnxRuntime")
                        // No batchifier: tensors already carry an explicit batch dimension.
                        .optTranslator(new NoopTranslator())
                        .build();
        return criteria.loadModel();
    }

    /**
     * Translates a single string.
     *
     * @param text source text
     * @return the translated text, or an empty string if nothing was generated
     * @throws Exception if tokenisation or inference fails
     */
    synchronized String translate(String text) throws Exception {
        Encoding encoding = tokenizer.encode(text);
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        if (inputIds.length == 0) {
            return "";
        }
        if (inputIds.length > MAX_INPUT_TOKENS) {
            inputIds = Arrays.copyOf(inputIds, MAX_INPUT_TOKENS);
            attentionMask = Arrays.copyOf(attentionMask, MAX_INPUT_TOKENS);
            // Marian expects the source sequence to end with EOS; restore it after truncation.
            inputIds[MAX_INPUT_TOKENS - 1] = eosTokenId;
            attentionMask[MAX_INPUT_TOKENS - 1] = 1L;
        }

        try (NDManager manager = NDManager.newBaseManager("OnnxRuntime")) {
            Shape sourceShape = new Shape(1, inputIds.length);

            NDArray encoderInputIds = manager.create(inputIds, sourceShape);
            encoderInputIds.setName("input_ids");
            NDArray encoderMask = manager.create(attentionMask, sourceShape);
            encoderMask.setName("attention_mask");

            NDList encoderOutput = encoder.predict(new NDList(encoderInputIds, encoderMask));
            NDArray encoderHiddenStates = encoderOutput.get(0);
            encoderHiddenStates.setName("encoder_hidden_states");

            // A distinct array from encoderMask: the decoder graph binds the same data
            // under a different input name, and one NDArray can only carry one name.
            NDArray crossAttentionMask = manager.create(attentionMask, sourceShape);
            crossAttentionMask.setName("encoder_attention_mask");

            long[] generated = new long[MAX_NEW_TOKENS];
            int generatedCount = 0;
            long[] decoderIds = {decoderStartTokenId};

            for (int step = 0; step < MAX_NEW_TOKENS; step++) {
                long nextToken;
                // This decoder export has no KV cache, so every step re-runs the whole
                // prefix and returns logits for all positions. Scoping each step to its
                // own manager releases those (large) tensors immediately.
                try (NDManager stepManager = manager.newSubManager()) {
                    NDArray decoderInputIds =
                            stepManager.create(decoderIds, new Shape(1, decoderIds.length));
                    decoderInputIds.setName("input_ids");

                    NDList decoderOutput =
                            decoder.predict(
                                    new NDList(
                                            decoderInputIds,
                                            crossAttentionMask,
                                            encoderHiddenStates));
                    nextToken = argMaxOfLastPosition(decoderOutput.get(0));
                }

                if (nextToken == eosTokenId) {
                    break;
                }
                generated[generatedCount++] = nextToken;
                decoderIds = Arrays.copyOf(decoderIds, decoderIds.length + 1);
                decoderIds[decoderIds.length - 1] = nextToken;
            }

            return tokenizer.decode(Arrays.copyOf(generated, generatedCount), true);
        }
    }

    /**
     * Greedy argmax over the vocabulary at the final sequence position of a
     * {@code (batch, sequence, vocab)} logits tensor.
     *
     * <p>Done in plain Java rather than via {@link NDArray#argMax()}: DJL's ONNX
     * Runtime {@code NDArray} implements almost no compute operations and
     * delegates {@code argMax} to an "alternative" engine (PyTorch), which this
     * mod does not ship — calling it would throw at runtime. Reading the tensor's
     * byte buffer is well-defined for the ONNX engine and copies nothing.
     */
    private static long argMaxOfLastPosition(NDArray logits) {
        Shape shape = logits.getShape();
        if (shape.dimension() != 3) {
            throw new IllegalStateException("Unexpected decoder logits shape: " + shape);
        }
        if (logits.getDataType() != DataType.FLOAT32) {
            throw new IllegalStateException("Unexpected decoder logits type: " + logits.getDataType());
        }
        long sequenceLength = shape.get(1);
        long vocabSize = shape.get(2);

        FloatBuffer values = logits.toByteBuffer().asFloatBuffer();
        int base = Math.toIntExact((sequenceLength - 1) * vocabSize);

        int bestIndex = 0;
        float bestValue = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vocabSize; i++) {
            float value = values.get(base + i);
            if (value > bestValue) {
                bestValue = value;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static String readGenerationConfig(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Missing " + ModelFiles.GENERATION_CONFIG + ": " + path);
        }
        return Files.readString(path);
    }

    /**
     * Extracts a required top-level integer field. generation_config.json is a
     * small flat object, so this avoids pulling in a JSON dependency for two
     * numbers.
     */
    private static long requireLong(String json, String key, Path modelDir) throws IOException {
        Matcher matcher =
                Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IOException(
                    "Missing \"" + key + "\" in " + ModelFiles.GENERATION_CONFIG + " for " + modelDir);
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new IOException("Malformed \"" + key + "\" in " + ModelFiles.GENERATION_CONFIG, e);
        }
    }

    @Override
    public void close() {
        closeQuietly(encoder);
        closeQuietly(decoder);
        closeQuietly(encoderModel);
        closeQuietly(decoderModel);
        closeQuietly(tokenizer);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Best-effort release of native resources.
        }
    }
}
