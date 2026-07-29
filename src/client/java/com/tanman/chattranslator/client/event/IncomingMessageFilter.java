package com.tanman.chattranslator.client.event;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides which incoming lines are worth offering a translation for.
 *
 * <p>Without this every join/leave line, scoreboard header, and the player's own
 * chat gets a translate hover and (in auto mode) a language-detection pass.
 * Pure logic so it can be unit tested without a running client.
 */
public final class IncomingMessageFilter {

    /**
     * Lingua needs real words to identify a language; below this a detection is a
     * coin flip, so short reactions ("gg", "xd", ":)") are left alone.
     */
    private static final int MIN_LETTERS = 4;

    private static final Pattern SERVER_EVENT = Pattern.compile(
            "\\b(joined the game|left the game|has joined|has left|joined the server"
                    + "|left the server|made the advancement|has made the advancement"
                    + "|completed the challenge|reached the goal|whispers to you"
                    + "|is now (afk|online|offline))\\b",
            Pattern.CASE_INSENSITIVE);

    /** Leading {@code <} and {@code [rank]} chunks that sit in front of a sender name. */
    private static final Pattern RANK_PREFIX =
            Pattern.compile("^[<\\s]*(?:\\[[^\\]]*\\][\\s<]*)*");

    /** What a real sender name is followed by, once rank prefixes are stripped. */
    private static final String SENDER_DELIMITERS = ">:»|-";

    private IncomingMessageFilter() {
    }

    /**
     * @param rendered  the whole chat line as shown, including any {@code <name>} prefix
     * @param body      the message text without the sender prefix
     * @param selfName  the local player's name, or {@code null} if unknown
     */
    public static boolean shouldOffer(String rendered, String body, String selfName) {
        if (body == null || body.isBlank()) {
            return false;
        }
        if (!hasEnoughLetters(body)) {
            return false;
        }
        if (isOwnMessage(rendered, selfName)) {
            return false;
        }
        return rendered == null || !SERVER_EVENT.matcher(rendered).find();
    }

    static boolean hasEnoughLetters(String text) {
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i)) && ++letters >= MIN_LETTERS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Own messages are already in the language the player typed, so translating them
     * wastes a model load (and, on paid backends, real money).
     */
    static boolean isOwnMessage(String rendered, String selfName) {
        if (rendered == null || selfName == null || selfName.isBlank()) {
            return false;
        }
        String name = selfName.toLowerCase(Locale.ROOT);
        // Strip the "<" and any "[rank]" chunks servers put in front of the name, so a
        // name that merely appears inside someone else's sentence is not mistaken for
        // the sender ("<Alice> Bob, come here" is not Bob's own message).
        String head = RANK_PREFIX.matcher(rendered.trim().toLowerCase(Locale.ROOT))
                .replaceFirst("");
        if (!head.startsWith(name)) {
            return false;
        }
        String rest = head.substring(name.length()).stripLeading();
        return !rest.isEmpty() && SENDER_DELIMITERS.indexOf(rest.charAt(0)) >= 0;
    }
}
