package adris.altoclef.player2api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a server will accept when a client tells it who its companions are.
 *
 * <h2>Why this exists</h2>
 *
 * Identity used to come from the server's own config file, which meant a player on someone else's
 * server got that operator's companions and could not change a thing. Now the client announces its
 * own roster at join — which is the right answer for the player and a <b>trust inversion</b> for the
 * server: a field that used to be written by the operator is now written by whoever is connecting,
 * and it ends up in three places that matter.
 *
 * <ul>
 *   <li><b>In chat</b>, as the companion's display name. A name carrying section signs can forge
 *       formatting, and a name matching another player's is an impersonation that reads as that
 *       player speaking.</li>
 *   <li><b>In the system prompt</b>, as the description and persona. Under {@code llm.clientBrain}
 *       the announcing player pays for those tokens, which is fine. When their client cannot think
 *       and the server falls back, the <b>operator</b> pays — so the length caps are a spend control
 *       and not tidiness.</li>
 *   <li><b>In the spawn suggestion list</b>, which is why there is a count cap at all.</li>
 * </ul>
 *
 * <h2>Clamp, do not reject the whole roster</h2>
 *
 * A single bad entry drops that entry; it does not silently refuse everything the player owns and
 * leave them unable to spawn anything. Over-long text is truncated rather than refused, because a
 * long persona is a person writing enthusiastically and not an attack. Every decision comes back in
 * {@link Result#rejections()} so it can be told to the player instead of happening invisibly — a
 * player whose companion did not appear needs to know their name collided, not just that nothing
 * worked.
 *
 * <p>Pure logic, no Minecraft: this is the part worth testing directly.
 */
public final class RosterGuard {
    private RosterGuard() {}

    /** Vanilla's own limit on a player name, and the sensible ceiling for a companion's. */
    public static final int MAX_NAME_CHARS = 16;

    /** Enough for a sentence about who they are; it is advertised in every prompt. */
    public static final int MAX_DESCRIPTION_CHARS = 500;

    /**
     * Enough for a real personality, capped because it is in the standing prompt of every single
     * turn. At roughly four characters per token this is ~500 tokens — measurable against the 14.7k
     * system prompt already measured, and bounded.
     */
    public static final int MAX_PERSONA_CHARS = 2000;

    /** One announced identity, in plain types so nothing here depends on the mod's records. */
    public record Identity(String name, String description, String persona, String skinFile,
                           String skinUsername, boolean skinSlim, String voice) {}

    /** What survived, and what to tell the player about what did not. */
    public record Result(List<Identity> accepted, List<String> rejections) {}

    /**
     * Sanitise a roster announced by a client.
     *
     * @param announced        what the client sent, in its order
     * @param onlinePlayerNames every player name currently on the server, for the impersonation
     *                          check. Case-insensitive comparison, because chat is read by people.
     */
    public static Result sanitize(List<Identity> announced, Set<String> onlinePlayerNames) {
        List<Identity> accepted = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        if (announced == null || announced.isEmpty()) {
            return new Result(accepted, rejections);
        }

        Set<String> lowerOnline = new LinkedHashSet<>();
        if (onlinePlayerNames != null) {
            for (String n : onlinePlayerNames) {
                if (n != null) {
                    lowerOnline.add(n.toLowerCase(Locale.ROOT));
                }
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        for (Identity entry : announced) {
            if (entry == null) {
                continue;
            }
            if (!ServerPolicy.withinCap(accepted.size(), ServerPolicy.maxRosterEntries)) {
                rejections.add("only the first " + ServerPolicy.maxRosterEntries
                        + " companions in your config were accepted by this server");
                break;
            }
            String name = entry.name() == null ? "" : stripControl(entry.name()).strip();
            if (name.isEmpty()) {
                rejections.add("a companion with no name was skipped");
                continue;
            }
            if (name.length() > MAX_NAME_CHARS) {
                rejections.add("'" + name + "' was skipped — names are limited to "
                        + MAX_NAME_CHARS + " characters here");
                continue;
            }
            if (lowerOnline.contains(name.toLowerCase(Locale.ROOT))) {
                // Not truncated or renamed: a companion named after a player is indistinguishable
                // from that player in chat, and picking a different name for them silently would
                // leave the player wondering who they are talking to.
                rejections.add("'" + name + "' was skipped — a player on this server has that name");
                continue;
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                rejections.add("'" + name + "' appears twice in your config; only the first was kept");
                continue;
            }
            accepted.add(new Identity(
                    name,
                    clamp(entry.description(), MAX_DESCRIPTION_CHARS),
                    clamp(entry.persona(), MAX_PERSONA_CHARS),
                    // Skin fields are read by the client that draws the companion and never reach a
                    // prompt or chat, but a path is still a path: keep them to a bare file name so an
                    // announced "../../" cannot be pointed at anything.
                    fileNameOnly(entry.skinFile()),
                    clamp(stripControl(entry.skinUsername()), MAX_NAME_CHARS),
                    entry.skinSlim(),
                    clamp(stripControl(entry.voice()), 64)));
        }
        return new Result(List.copyOf(accepted), List.copyOf(rejections));
    }

    /**
     * Remove section signs and anything that would break out of a single chat line.
     *
     * <p>Section signs are the formatting escape: left in, a name can paint itself, hide itself, or
     * imitate a system message. Newlines and carriage returns would split one line into two, which
     * is how a name becomes a fake second message.
     */
    private static String stripControl(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '§' || c == '\n' || c == '\r' || c == '\t' || c < ' ') {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Truncate rather than refuse: enthusiasm is not an attack, but it is not unbounded either. */
    private static String clamp(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.strip();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    /** The last path segment and nothing else, so an announced skin path cannot traverse. */
    private static String fileNameOnly(String value) {
        String cleaned = stripControl(value).strip();
        if (cleaned.isEmpty()) {
            return "";
        }
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String name = slash < 0 ? cleaned : cleaned.substring(slash + 1);
        // ".." survives the split above and names a directory, not a file.
        return name.equals(".") || name.equals("..") ? "" : name;
    }
}
