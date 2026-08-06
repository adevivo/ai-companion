/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package adris.altoclef;

import adris.altoclef.player2api.manager.TTSManager;
import adris.altoclef.player2api.utils.AudioUtils;
import baritone.KeepName;
import baritone.PlayerEngine;
import baritone.client.CustomFishingBobberRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@KeepName
public final class PlayerEngineClient implements ClientModInitializer {
   public void onInitializeClient() {
      EntityRendererRegistry.register(PlayerEngine.FISHING_BOBBER, CustomFishingBobberRenderer::new);

      // Companion speech: the server tells us what to say and where to synthesize it (local Kokoro);
      // we fetch and play it here so the audio lands on this player's speakers. Read the buf on the
      // network thread, then do the blocking HTTP + playback off it.
      ClientPlayNetworking.registerGlobalReceiver(TTSManager.SPEAK_CHANNEL, (client, handler, buf, responseSender) -> {
         UUID speaker = buf.readUUID();
         String endpoint = buf.readUtf();
         String model = buf.readUtf();
         String voice = buf.readUtf();
         String text = buf.readUtf();
         double speed = buf.readDouble();

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
