package com.player2.playerengine;

import com.neovetta.aicompanion.core.TtsConfig;
import com.neovetta.aicompanion.core.TtsConfig;
import com.player2.playerengine.player2api.manager.TTSManager;
import com.player2.playerengine.player2api.utils.AudioUtils;
import com.player2.playerengine.automaton.KeepName;
import com.player2.playerengine.automaton.client.CustomFishingBobberRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import net.minecraft.resources.Identifier;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@KeepName
public final class PlayerEngineClient {
   public static void onInitializeClient() {
      EntityRendererRegistry.register(()->PlayerEngine.FISHING_BOBBER, CustomFishingBobberRenderer::new);

      // Companion speech: the server tells us what to say, in which voice; we decide WHERE to
      // synthesize it (our own Kokoro endpoint) and play it here, so the audio lands on this
      // player's speakers. Read the buf on the network thread, then do the blocking HTTP +
      // playback off it.
      NetworkManager.registerReceiver(NetworkManager.Side.S2C, TTSManager.SPEAK_CHANNEL, (buf, context) -> {
         UUID speaker = buf.readUUID();
         String serverEndpoint = buf.readUtf();
         String model = buf.readUtf();
         String voice = buf.readUtf();
         String text = buf.readUtf();
         double speed = buf.readDouble();

         // THIS machine's endpoint wins, because THIS machine is the one that has to reach it.
         // The server's copy of tts.endpoint describes the server's network, and on a dedicated
         // server that meant the default "http://localhost:8880" was sent to every client no matter
         // what the player had configured — the endpoint in their own file and config screen was
         // applied to their own TtsConfig and then thrown away here. The wire field is kept as a
         // fallback for a blank local value, and so the packet format does not change.
         String local = TtsConfig.normalizedEndpoint();
         String endpoint = local.isBlank() ? serverEndpoint : local;

         CompletableFuture.runAsync(() -> {
            // streamAudio blocks until the audio has finished, so this reply doubles as "the
            // companion has stopped talking" — the server holds its speech lock until it arrives
            // rather than guessing a duration, and drops it immediately when the answer is "no
            // Kokoro here". Only this machine can tell it either way.
            boolean spoken = AudioUtils.streamAudio(endpoint, model, voice, text, speed);
            // Back on the client thread, and built there: sending is not safe from the pool, and a
            // buffer allocated for a send that then gets skipped is a leaked netty buffer. Leaving
            // the world mid-sentence is the ordinary way to arrive here with nowhere to send, and
            // the server's own guard covers the ack it never gets.
            client.execute(() -> {
               if (!ClientPlayNetworking.canSend(TTSManager.ACK_CHANNEL)) {
                  return;
               }
               FriendlyByteBuf ack = PacketByteBufs.create();
               ack.writeUUID(speaker);
               ack.writeBoolean(spoken);
               ClientPlayNetworking.send(TTSManager.ACK_CHANNEL, ack);
            });
         });
      });
   }
}
