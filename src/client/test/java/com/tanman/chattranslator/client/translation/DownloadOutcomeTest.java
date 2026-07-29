package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadOutcomeTest {

    @Test
    void classifiesMissingRepoStatusesAsNotAvailable() {
        assertEquals(DownloadOutcome.NOT_AVAILABLE, ModelDownloader.classifyHttpStatus(401));
        assertEquals(DownloadOutcome.NOT_AVAILABLE, ModelDownloader.classifyHttpStatus(403));
        assertEquals(DownloadOutcome.NOT_AVAILABLE, ModelDownloader.classifyHttpStatus(404));
    }

    @Test
    void classifiesServerAndOtherErrorsAsFailed() {
        assertEquals(DownloadOutcome.FAILED, ModelDownloader.classifyHttpStatus(500));
        assertEquals(DownloadOutcome.FAILED, ModelDownloader.classifyHttpStatus(502));
        assertEquals(DownloadOutcome.FAILED, ModelDownloader.classifyHttpStatus(429));
        assertEquals(DownloadOutcome.FAILED, ModelDownloader.classifyHttpStatus(418));
    }

    @Test
    void successIsOk() {
        assertTrue(DownloadOutcome.SUCCESS.ok());
        assertFalse(DownloadOutcome.NOT_AVAILABLE.ok());
        assertFalse(DownloadOutcome.FAILED.ok());
    }
}
