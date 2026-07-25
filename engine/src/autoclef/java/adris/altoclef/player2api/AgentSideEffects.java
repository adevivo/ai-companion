
package adris.altoclef.player2api;

import java.util.function.Consumer;

import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.player2api.manager.TTSManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.CommandExecutor;
import adris.altoclef.tasks.LookAtOwnerTask;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class AgentSideEffects {
    private static final Logger LOGGER = LogManager.getLogger();

    public sealed interface CommandExecutionStopReason
            permits CommandExecutionStopReason.Cancelled,
            CommandExecutionStopReason.Finished,
            CommandExecutionStopReason.Error {
        String commandName();

        record Cancelled(String commandName) implements CommandExecutionStopReason {
        }

        record Finished(String commandName) implements CommandExecutionStopReason {
        }

        record Error(String commandName, String errMsg) implements CommandExecutionStopReason {
        }
    }

    public static void onEntityMessage(MinecraftServer server, Event.CharacterMessage characterMessage) {
        // message part:
        if (characterMessage.message() != null && !characterMessage.message().isBlank()) {
            AgentConversationData sendingCharacterData = characterMessage.sendingCharacterData();
            String message = String.format("<%s> %s", sendingCharacterData.getName(), characterMessage.message());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // if you are an owner, or close, send to player.
                // if(sendingCharacterData.isOwner(player.getUUID()) ||
                // isClose(sendingCharacterData, player) ){
                broadcastChatToPlayer(server, message, player);
                // }
            }
            TTSManager.TTS(characterMessage.message(), sendingCharacterData.getCharacter(),
                    sendingCharacterData.getPlayer2apiService());
            ConversationManager.onAICharacterMessage(characterMessage,
                    characterMessage.sendingCharacterData().getUUID());
        }

        // command part:
        if (characterMessage.command() != null && !characterMessage.command().isBlank()) {
            onCommandListGenerated(characterMessage.sendingCharacterData().getMod(), characterMessage.command(),
                    characterMessage.sendingCharacterData()::onCommandFinish);
        }
    }

    public static void onError(MinecraftServer server, String errMsg) {
        LOGGER.error(errMsg);
        // The LLM request cap otherwise fails silently in-world: the companion just goes mute and
        // the reason lives only in the log. Surface it in chat so the player knows to raise
        // llm.maxRequests or restart, instead of assuming the mod froze/broke.
        if (errMsg != null && errMsg.contains("cap reached")) {
            LOGGER.error("Companion hit the LLM request guardrail (llm.maxRequests). It will not respond "
                    + "again until you restart the session (or set llm.maxRequests to 0 for unlimited).");
            String notice = "[companion] Thinking budget reached for this session (llm.maxRequests). "
                    + "Raise the cap in config or restart the world to continue.";
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                broadcastChatToPlayer(server, notice, player);
            }
        }
    }

    public static void onCommandListGenerated(AltoClefController mod, String command,
            Consumer<CommandExecutionStopReason> onStop) {
        CommandExecutor cmdExecutor = mod.getCommandExecutor();
        String commandWithPrefix = cmdExecutor.isClientCommand(command) ? command
                : (cmdExecutor.getCommandPrefix() + command);
        if (commandWithPrefix.equals("@stop")) {
            mod.isStopping = true;
        } else {
            mod.isStopping = false;
        }
        // `idle` and `bodylang` are the model's two ways of saying "nothing to do here" — one stands by,
        // the other waves. Both used to run through the task chain, which REPLACES whatever work is in
        // flight, and the LookAtOwnerTask that follows then leaves the agent idle for good. So a nod
        // hello or an "I'm already on it" in reply to small talk would silently kill a 20-minute `farm`
        // or `get`, and the model would keep reporting progress on a task that no longer existed.
        //
        // Neither is worth cancelling real work for. Skip them while a non-idle user task is running;
        // the spoken reply still goes out, only the gesture is dropped. `stop` remains the way to
        // actually abort a task.
        boolean cosmetic = commandWithPrefix.contains("idle") || commandWithPrefix.startsWith("@bodylang");
        if (cosmetic && mod.getUserTaskChain().isActive() && !mod.getUserTaskChain().isRunningIdleTask()) {
            LOGGER.info("Skipping cosmetic {} — a user task is already running", commandWithPrefix);
            return;
        }
        if (commandWithPrefix.contains("idle")) {
            mod.runUserTask(new LookAtOwnerTask());
            return;
        }

        // add quotes to build_structure so it gets proccessed as one arg:
        String processedCommandWithPrefix = commandWithPrefix.replaceFirst(
                "^(@build_structure)\\s+(?![\"'])(.+)$",
                "$1 \"$2\"");

        cmdExecutor.execute(processedCommandWithPrefix, () -> {
            if (mod.isStopping) {
                LOGGER.info(
                        "[AgentSideEffects/AgentSideEffects]: (%s) was cancelled. Not adding finish event to queue.",
                        processedCommandWithPrefix);
                // Other canceled logic here
                onStop.accept(new CommandExecutionStopReason.Cancelled(commandWithPrefix));
                LOGGER.info("after cancel, not running look at owner");
            } else {
                if (!commandWithPrefix.equals("@bodylang greeting")) {
                    LOGGER.info("Running on stop after finish cmd={}", commandWithPrefix);
                    onStop.accept(new CommandExecutionStopReason.Finished(commandWithPrefix));
                } else {
                    LOGGER.info("Ignore onStop for bodylang greeting");
                }
                LOGGER.info("Running look at owner task after finish cmd={}", commandWithPrefix);
                mod.runUserTask(new LookAtOwnerTask());
            }
        }, (err) -> {
            onStop.accept(new CommandExecutionStopReason.Error(commandWithPrefix, err.getMessage()));
            LOGGER.info("Running look at owner aftr error in cmd={}", commandWithPrefix);
            mod.runUserTask(new LookAtOwnerTask());
        });
    }

    private static void broadcastChatToPlayer(MinecraftServer server, String message, ServerPlayer player) {
        player.displayClientMessage(Component.literal(message), false);
    }

}