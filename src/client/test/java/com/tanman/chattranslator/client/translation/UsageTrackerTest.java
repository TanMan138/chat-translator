package com.tanman.chattranslator.client.translation;

import com.tanman.chattranslator.client.translation.UsageTracker.Level;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageTrackerTest {

    @Test
    void accumulatesPerProvider() {
        UsageTracker tracker = new UsageTracker("2026-07", Map.of());
        tracker.record("google", "2026-07", 100);
        tracker.record("google", "2026-07", 50);
        tracker.record("langbly", "2026-07", 7);

        assertEquals(150, tracker.used("google", "2026-07"));
        assertEquals(7, tracker.used("langbly", "2026-07"));
        assertEquals(0, tracker.used("deepl", "2026-07"));
    }

    @Test
    void restoresPreviouslySavedCounts() {
        UsageTracker tracker = new UsageTracker("2026-07", Map.of("google", 400_000L));

        assertEquals(400_000, tracker.used("google", "2026-07"));
    }

    @Test
    void newMonthClearsEveryCounter() {
        UsageTracker tracker = new UsageTracker("2026-07", Map.of("google", 400_000L));
        tracker.record("langbly", "2026-07", 1000);

        assertEquals(0, tracker.used("google", "2026-08"));
        assertEquals(0, tracker.used("langbly", "2026-08"));
        assertEquals("2026-08", tracker.month());
    }

    @Test
    void recordingInANewMonthStartsFromThatRecord() {
        UsageTracker tracker = new UsageTracker("2026-07", Map.of("google", 400_000L));

        assertEquals(25, tracker.record("google", "2026-08", 25));
    }

    @Test
    void thresholdsFollowThePercentageOfBudget() {
        assertEquals(Level.OK, UsageTracker.level(0, 500_000));
        assertEquals(Level.OK, UsageTracker.level(399_999, 500_000));
        assertEquals(Level.WARN_80, UsageTracker.level(400_000, 500_000));
        assertEquals(Level.WARN_80, UsageTracker.level(474_999, 500_000));
        assertEquals(Level.WARN_95, UsageTracker.level(475_000, 500_000));
        assertEquals(Level.EXCEEDED, UsageTracker.level(500_000, 500_000));
        assertEquals(Level.EXCEEDED, UsageTracker.level(900_000, 500_000));
    }

    @Test
    void aBudgetOfZeroMeansNoLimit() {
        assertEquals(Level.OK, UsageTracker.level(50_000_000, 0));
        assertEquals(Level.OK, UsageTracker.level(0, 0));
    }

    @Test
    void snapshotCanBePersistedAndReloaded() {
        UsageTracker tracker = new UsageTracker("2026-07", Map.of());
        tracker.record("google", "2026-07", 1234);

        UsageTracker reloaded = new UsageTracker(tracker.month(), tracker.snapshot());

        assertEquals(1234, reloaded.used("google", "2026-07"));
    }

    @Test
    void snapshotDoesNotShareStateWithTheTracker() {
        UsageTracker tracker = new UsageTracker("2026-07", Map.of());
        tracker.record("google", "2026-07", 10);

        Map<String, Long> snapshot = tracker.snapshot();
        tracker.record("google", "2026-07", 10);

        assertTrue(snapshot.get("google") == 10L);
    }
}
