package com.tanman.chattranslator.client.translation;

/**
 * File names used inside a cached model directory.
 *
 * <p>The {@code Xenova/opus-mt-*} repositories do not ship a single fused
 * translation graph. Their ONNX export is split into a separate encoder and
 * decoder, so a cached model directory holds two {@code .onnx} files rather
 * than one. These constants are shared by {@link ModelDownloader} (which writes
 * them), {@link ModelManager} (which checks for them) and {@link Translator}
 * (which loads them) so the three cannot drift apart.
 */
public final class ModelFiles {

    /** Encoder graph: {@code input_ids}, {@code attention_mask} -> {@code last_hidden_state}. */
    public static final String ENCODER = "encoder_model.onnx";

    /**
     * Decoder graph (the non-KV-cached export): {@code input_ids},
     * {@code encoder_attention_mask}, {@code encoder_hidden_states} -> {@code logits}
     * (plus {@code present.*} tensors we ignore).
     */
    public static final String DECODER = "decoder_model.onnx";

    /** Hugging Face fast-tokenizer definition. */
    public static final String TOKENIZER = "tokenizer.json";

    /** Supplies {@code decoder_start_token_id} / {@code eos_token_id}, which vary per language pair. */
    public static final String GENERATION_CONFIG = "generation_config.json";

    private ModelFiles() {
    }
}
