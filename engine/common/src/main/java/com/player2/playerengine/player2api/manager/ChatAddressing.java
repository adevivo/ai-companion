package com.player2.playerengine.player2api.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Who a chat line is aimed at, read from its words alone. Minecraft-free so it can be tested.
 *
 * <p>Used by {@link ConversationManager} when other people are online: an owner's line then reaches
 * their companion only when it names her, or she has just spoken to them, and never when it names
 * another player instead. Found 2026-09-26 on holly: "hiya stonewolf!" and "StoneWolf_ hiya! :)",
 * both said to another player, were answered by the companion on the speaker's behalf.
 *
 * <p>Matching is whole words only and deliberately exact. A prefix rule ("stone" for StoneWolf_)
 * would catch "place a stone"; a miss here costs one unrouted line, a false match costs a reply
 * nobody asked for.
 */
public final class ChatAddressing {
    private ChatAddressing() {}

    /** Letters and digits, lowercased; everything else splits words. "StoneWolf_ hiya!" → [stonewolf, hiya]. */
    static List<String> words(String message) {
        List<String> out = new ArrayList<>();
        if (message == null) {
            return out;
        }
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                word.append(Character.toLowerCase(c));
            } else if (word.length() > 0) {
                out.add(word.toString());
                word.setLength(0);
            }
        }
        if (word.length() > 0) {
            out.add(word.toString());
        }
        return out;
    }

    /** A name reduced the same way: "StoneWolf_" → "stonewolf". */
    static String normalize(String name) {
        return name == null ? "" : String.join("", words(name));
    }

    /** Whether the line contains the companion's name as a word ("thanks ava", "Ava!"). */
    public static boolean mentions(String message, String name) {
        String n = normalize(name);
        return !n.isEmpty() && words(message).contains(n);
    }

    /**
     * Whether the line names a player: their whole name, or their name without trailing digits
     * ("dauk" for Dauk808), which is how people shorten usernames in chat. The digit-less form must
     * be at least 3 letters, so "Al42" is not triggered by "al".
     */
    public static boolean mentionsPlayer(String message, String playerName) {
        String full = normalize(playerName);
        if (full.isEmpty()) {
            return false;
        }
        String bare = full.replaceAll("[0-9]+$", "");
        List<String> w = words(message);
        return w.contains(full) || (bare.length() >= 3 && !bare.equals(full) && w.contains(bare));
    }

    /** Whether any of these players is named in the line. */
    public static boolean mentionsAnyPlayer(String message, Collection<String> playerNames) {
        for (String p : playerNames) {
            if (mentionsPlayer(message, p)) {
                return true;
            }
        }
        return false;
    }
}
