package com.tanman.chattranslator.client.translation;

/**
 * Result of an on-device OPUS-MT model download attempt.
 */
public enum DownloadOutcome {
    /** All required files landed on disk. */
    SUCCESS,
    /**
     * Hugging Face has no published Xenova OPUS-MT repo for this pair
     * (commonly HTTP 401/403/404 for missing repos).
     */
    NOT_AVAILABLE,
    /** Network error, timeout, or other transient failure. */
    FAILED;

    public boolean ok() {
        return this == SUCCESS;
    }
}
