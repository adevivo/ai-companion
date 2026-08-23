package com.neovetta.aicompanion.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CompanionConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The config path that does not need Cloth Config installed.
 *
 * <p><b>Why this exists.</b> Cloth Config used to be bundled inside our jar (Jar-in-Jar) and listed
 * under {@code depends}, which meant two things we did not want: CurseForge saw us redistributing
 * another author's separately-published mod, and a player who ended up without it got a
 * launch-blocking Fabric Loader dependency error rather than a mod that merely looked plainer.
 * Cloth is now {@code suggests} and unbundled, so it has to be genuinely optional at runtime.
 *
 * <p><b>Scope.</b> This edits {@code config/aicompanion.json} — the caller's own local file, the
 * exact one {@link CompanionConfigScreen} edits. That is deliberate and matches the trust model:
 * the client holds the API key, so the client's settings are edited by the client. Nothing here
 * touches server-owned policy, which stays behind the operator-gated {@code /companion} tree.
 *
 * <p><b>Deliberately not a reimplementation of the screen.</b> The screen is a thousand lines of
 * per-setting widgets, dropdowns and validation. This is a generic dotted-path reader/writer over
 * the same JSON, which buys full coverage in a fraction of the surface, and it is fenced by two
 * rules that make the generic form safe:
 *
 * <ul>
 *   <li><b>A path must already exist to be set.</b> You cannot invent a key. That is a direct guard
 *       against the failure mode where a setting that was never plumbed through
 *       {@link CompanionConfig} sits in the file doing nothing, silently.</li>
 *   <li><b>The existing JSON type wins.</b> A number stays a number, a boolean stays a boolean. A
 *       value that will not parse as the current type is refused, naming the type it wanted, rather
 *       than quietly turning a number into a string that a provider rejects later.</li>
 * </ul>
 *
 * <p>Writes copy the file to {@code .bak} first and are then handed to the ordinary reload path, so
 * a chat edit lands exactly the way a screen edit does rather than becoming a second code path.
 */
public final class CompanionConfigChat {

    private CompanionConfigChat() {}

    /** Cloth's mod id. Checked at runtime, never imported — this class must link without Cloth. */
    private static final String CLOTH_ID = "cloth-config";

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Substrings that mark a value as secret; shown redacted by {@code show} and {@code get}. */
    private static final List<String> SECRET_HINTS =
            List.of("apikey", "api_key", "token", "secret", "password");

    public static boolean clothPresent() {
        return FabricLoader.getInstance().isModLoaded(CLOTH_ID);
    }

    /**
     * Handle {@code /companion config}. Opens the Cloth screen when Cloth is there, and otherwise
     * explains the chat route instead of failing.
     *
     * <p>The screen class is reached only through {@link ClothScreenHolder}, so that when Cloth is
     * absent the JVM never has to resolve {@link CompanionConfigScreen} and its Cloth imports.
     */
    public static void openOrExplain(Screen parent) {
        if (clothPresent()) {
            ClothScreenHolder.open(parent);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        for (Component line : noClothExplanation()) {
            // displayClientMessage(component, false), not sendSystemMessage: Entity lost
            // sendSystemMessage in 1.21.11, and the false puts this in the chat log rather than
            // flashing it over the hotbar where a five-line explanation would be unreadable.
            client.player.displayClientMessage(line, false);
        }
    }

    /**
     * Indirection so {@link CompanionConfigScreen} — and through it every {@code me.shedaniel}
     * class — is loaded only when it is actually going to be used. Resolution is lazy per class,
     * not per branch, so keeping the reference behind its own type is what makes the Cloth-absent
     * path safe rather than merely untaken.
     */
    private static final class ClothScreenHolder {
        private ClothScreenHolder() {}

        static void open(Screen parent) {
            Minecraft.getInstance().setScreen(CompanionConfigScreen.create(parent));
        }
    }

    private static List<Component> noClothExplanation() {
        Path path = CompanionConfig.configPath();
        List<Component> out = new ArrayList<>();
        out.add(Component.literal("The config screen needs Cloth Config, which is not installed.")
                .withStyle(ChatFormatting.YELLOW));
        out.add(Component.literal("Install ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Cloth Config API").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" for the full screen, or edit settings from chat:")
                        .withStyle(ChatFormatting.GRAY)));
        out.add(usage("/aicompanion config show", "list every setting and its value"));
        out.add(usage("/aicompanion config get <path>", "read one, e.g. llm.model"));
        out.add(usage("/aicompanion config set <path> <value>", "write one, then reload"));
        out.add(Component.literal("The file itself: ").withStyle(ChatFormatting.DARK_GRAY)
                .append(clickToCopy(path.toString())));
        return out;
    }

    private static Component usage(String command, String what) {
        return Component.literal("  " + command).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  " + what).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent clickToCopy(String text) {
        return Component.literal(text).withStyle(Style.EMPTY
                .withColor(ChatFormatting.WHITE)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(text)));
    }

    /**
     * Register {@code /aicompanion config ...}. A CLIENT command: it never reaches the server, works
     * in single player and on any server, and edits only the local file.
     *
     * <p>Registered unconditionally, not only when Cloth is missing. Someone on a dedicated server
     * wanting to change one value without opening a screen has the same use for it, and a command
     * that appears and disappears depending on another mod is worse to document than one that is
     * simply always there.
     */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("aicompanion")
                        .then(ClientCommandManager.literal("config")
                                .executes(ctx -> show(ctx.getSource()))
                                .then(ClientCommandManager.literal("show")
                                        .executes(ctx -> show(ctx.getSource())))
                                .then(ClientCommandManager.literal("path")
                                        .executes(ctx -> path(ctx.getSource())))
                                .then(ClientCommandManager.literal("get")
                                        .then(ClientCommandManager.argument("path", StringArgumentType.string())
                                                .executes(ctx -> get(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "path")))))
                                .then(ClientCommandManager.literal("set")
                                        .then(ClientCommandManager.argument("path", StringArgumentType.string())
                                                .then(ClientCommandManager.argument("value",
                                                                StringArgumentType.greedyString())
                                                        .executes(ctx -> set(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "path"),
                                                                StringArgumentType.getString(ctx, "value")))))))));
    }

    // ---------------------------------------------------------------- subcommands

    private static int path(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Config file: ").withStyle(ChatFormatting.GRAY)
                .append(clickToCopy(CompanionConfig.configPath().toString())));
        return 1;
    }

    private static int show(FabricClientCommandSource source) {
        JsonObject root = read(source);
        if (root == null) {
            return 0;
        }
        List<Leaf> leaves = new ArrayList<>();
        flatten("", root, leaves);
        source.sendFeedback(Component.literal("aicompanion.json - " + leaves.size() + " settings")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (Leaf leaf : leaves) {
            source.sendFeedback(Component.literal("  " + leaf.path()).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" = ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(redactIfSecret(leaf.path(), leaf.value()))
                            .withStyle(ChatFormatting.WHITE)));
        }
        source.sendFeedback(Component.literal("Change one with /aicompanion config set <path> <value>")
                .withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int get(FabricClientCommandSource source, String dotted) {
        JsonObject root = read(source);
        if (root == null) {
            return 0;
        }
        JsonElement found = resolve(root, dotted);
        if (found == null) {
            source.sendError(Component.literal("No such setting: " + dotted
                    + " (try /aicompanion config show)"));
            return 0;
        }
        String rendered = found.isJsonPrimitive() ? found.getAsString() : PRETTY.toJson(found);
        source.sendFeedback(Component.literal(dotted).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" = ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(redactIfSecret(dotted, rendered)).withStyle(ChatFormatting.WHITE)));
        return 1;
    }

    private static int set(FabricClientCommandSource source, String dotted, String raw) {
        Path file = CompanionConfig.configPath();
        JsonObject root = read(source);
        if (root == null) {
            return 0;
        }

        JsonElement existing = resolve(root, dotted);
        if (existing == null) {
            source.sendError(Component.literal("No such setting: " + dotted
                    + ". Only settings already in the file can be set - a new key would be read by"
                    + " nothing. See /aicompanion config show."));
            return 0;
        }
        if (!existing.isJsonPrimitive()) {
            source.sendError(Component.literal(dotted + " is an object or list, not a single value."
                    + " Edit it in the file: " + file));
            return 0;
        }

        JsonPrimitive typed;
        try {
            typed = coerceToTypeOf(existing.getAsJsonPrimitive(), raw);
        } catch (IllegalArgumentException e) {   // NumberFormatException is one of these
            source.sendError(Component.literal("Cannot set " + dotted + " to \"" + raw + "\" - it must be "
                    + describeType(existing.getAsJsonPrimitive()) + "."));
            return 0;
        }

        String before = redactIfSecret(dotted, existing.getAsString());
        assign(root, dotted, typed);

        try {
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, PRETTY.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            source.sendError(Component.literal("Could not write " + file + ": " + e.getMessage()));
            AiCompanion.LOGGER.warn("[{}] chat config write failed", AiCompanion.MOD_ID, e);
            return 0;
        }

        source.sendFeedback(Component.literal(dotted).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(before).withStyle(ChatFormatting.RED))
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(redactIfSecret(dotted, typed.getAsString()))
                        .withStyle(ChatFormatting.GREEN)));

        // Apply it the way the screen's Save does, so a chat edit is not a second code path.
        try {
            CompanionConfig.reloadClientOwned();
            source.sendFeedback(Component.literal("Applied. Server-owned settings still need"
                    + " /companion reload from an operator.").withStyle(ChatFormatting.DARK_GRAY));
        } catch (RuntimeException e) {
            source.sendError(Component.literal("Saved, but reloading it failed: " + e.getMessage()
                    + " - the previous file is at " + file.getFileName() + ".bak"));
            AiCompanion.LOGGER.warn("[{}] chat config reload failed", AiCompanion.MOD_ID, e);
            return 0;
        }
        return 1;
    }

    // ---------------------------------------------------------------- json helpers

    private static JsonObject read(FabricClientCommandSource source) {
        Path file = CompanionConfig.configPath();
        if (!Files.exists(file)) {
            source.sendError(Component.literal("No config file yet at " + file
                    + " - it is written on first world load."));
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            source.sendError(Component.literal("Could not read " + file + ": " + e.getMessage()));
            return null;
        }
    }

    /** Walk a dotted path. Returns null rather than throwing when any step is missing. */
    private static JsonElement resolve(JsonObject root, String dotted) {
        JsonElement cursor = root;
        for (String part : dotted.split("\\.")) {
            if (cursor == null || !cursor.isJsonObject()) {
                return null;
            }
            cursor = cursor.getAsJsonObject().get(part);
        }
        return cursor;
    }

    /** Replace the value at a dotted path. Only called after {@link #resolve} proved it exists. */
    private static void assign(JsonObject root, String dotted, JsonElement value) {
        String[] parts = dotted.split("\\.");
        JsonObject cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            cursor = cursor.getAsJsonObject(parts[i]);
        }
        cursor.add(parts[parts.length - 1], value);
    }

    /**
     * Parse {@code raw} as whatever the current value already is. Keeping the type is what stops a
     * chat edit from turning a number into a string that a provider rejects at request time, far
     * away from the edit that caused it.
     */
    private static JsonPrimitive coerceToTypeOf(JsonPrimitive current, String raw) {
        String trimmed = raw.trim();
        if (current.isBoolean()) {
            if (trimmed.equalsIgnoreCase("true")) {
                return new JsonPrimitive(true);
            }
            if (trimmed.equalsIgnoreCase("false")) {
                return new JsonPrimitive(false);
            }
            throw new IllegalArgumentException("not a boolean");
        }
        if (current.isNumber()) {
            // Whole numbers stay whole: writing 1.0 where a count is expected reads badly, and some
            // consumers are strict about it.
            if (isWhole(current)) {
                return new JsonPrimitive(Long.parseLong(trimmed));
            }
            return new JsonPrimitive(Double.parseDouble(trimmed));
        }
        // Strings are taken as typed, including empty, which is how a value is cleared.
        return new JsonPrimitive(raw);
    }

    private static boolean isWhole(JsonPrimitive number) {
        return number.getAsString().matches("-?\\d+");
    }

    private static String describeType(JsonPrimitive p) {
        if (p.isBoolean()) {
            return "true or false";
        }
        if (p.isNumber()) {
            return isWhole(p) ? "a whole number" : "a number";
        }
        return "text";
    }

    private record Leaf(String path, String value) {}

    /** Depth-first walk collecting every primitive leaf, in file order. */
    private static void flatten(String prefix, JsonObject object, List<Leaf> out) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                flatten(path, value.getAsJsonObject(), out);
            } else if (value.isJsonArray()) {
                out.add(new Leaf(path, "[" + value.getAsJsonArray().size() + " items - edit in the file]"));
            } else if (value.isJsonNull()) {
                out.add(new Leaf(path, "(unset)"));
            } else {
                out.add(new Leaf(path, value.getAsString()));
            }
        }
    }

    /**
     * Chat is logged and screenshotted. An API key that scrolls past in {@code show} is an API key
     * in someone's upload, so anything that looks like a credential prints as its length only.
     */
    private static String redactIfSecret(String dotted, String value) {
        String lower = dotted.toLowerCase(Locale.ROOT);
        for (String hint : SECRET_HINTS) {
            if (lower.contains(hint)) {
                return value.isEmpty() ? "(unset)" : "(" + value.length() + " chars, hidden)";
            }
        }
        return value;
    }
}
