package com.tanman.chattranslator.client.translation;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts characters sent to paid translation services, per provider, per month.
 *
 * <p>Google and Langbly both bill automatically once their included allowance runs
 * out, and neither publishes a usage endpoint an API key may call — so the only way
 * to warn a player before they are charged is to count locally.
 *
 * <p>This is an <strong>estimate</strong>, deliberately: it sees only what this mod
 * sends, not the same key used elsewhere, and its calendar month will not line up
 * exactly with a provider's billing period. Good enough to warn, not a billing
 * guarantee — nothing built on it should claim otherwise.
 *
 * <p>The current month is passed in rather than read from a clock, so rollover is
 * testable.
 */
public final class UsageTracker {

    /** How close to the budget the usage has climbed. */
    public enum Level {
        OK,
        WARN_80,
        WARN_95,
        EXCEEDED
    }

    private final Map<String, Long> characters = new HashMap<>();
    private String month;

    public UsageTracker(String month, Map<String, Long> initial) {
        this.month = month == null ? "" : month;
        if (initial != null) {
            initial.forEach((provider, used) -> {
                if (provider != null && used != null && used > 0) {
                    characters.put(provider, used);
                }
            });
        }
    }

    /**
     * Adds usage for a provider and returns that provider's new monthly total.
     * A different month from the stored one clears every counter first.
     */
    public synchronized long record(String provider, String currentMonth, int addedCharacters) {
        rollOver(currentMonth);
        if (provider == null || addedCharacters <= 0) {
            return characters.getOrDefault(provider, 0L);
        }
        return characters.merge(provider, (long) addedCharacters, Long::sum);
    }

    public synchronized long used(String provider, String currentMonth) {
        rollOver(currentMonth);
        return characters.getOrDefault(provider, 0L);
    }

    public synchronized String month() {
        return month;
    }

    public synchronized Map<String, Long> snapshot() {
        return new HashMap<>(characters);
    }

    /**
     * @param budget characters allowed per month, or {@code 0} for no limit
     */
    public static Level level(long used, long budget) {
        if (budget <= 0) {
            return Level.OK;
        }
        if (used >= budget) {
            return Level.EXCEEDED;
        }
        if (used * 100 >= budget * 95) {
            return Level.WARN_95;
        }
        if (used * 100 >= budget * 80) {
            return Level.WARN_80;
        }
        return Level.OK;
    }

    private void rollOver(String currentMonth) {
        if (currentMonth == null || currentMonth.equals(month)) {
            return;
        }
        month = currentMonth;
        characters.clear();
    }
}
