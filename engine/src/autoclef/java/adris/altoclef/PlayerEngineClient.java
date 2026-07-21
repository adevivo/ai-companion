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

import adris.altoclef.player2api.utils.AudioUtils;
import baritone.KeepName;
import baritone.PlayerEngine;
import baritone.client.CustomFishingBobberRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

@KeepName
public final class PlayerEngineClient implements ClientModInitializer {
   public void onInitializeClient() {
      EntityRendererRegistry.register(PlayerEngine.FISHING_BOBBER, CustomFishingBobberRenderer::new);

      // Companion speech: the server tells us what to say and where to synthesize it (local Kokoro);
      // we fetch and play it here so the audio lands on this player's speakers. Read the buf on the
      // network thread, then do the blocking HTTP + playback off it.
      ClientPlayNetworking.registerGlobalReceiver(new ResourceLocation("playerengine", "stream_tts"), (client, handler, buf, responseSender) -> {
         String endpoint = buf.readUtf();
         String model = buf.readUtf();
         String voice = buf.readUtf();
         String text = buf.readUtf();
         double speed = buf.readDouble();

         CompletableFuture.runAsync(() -> {
            AudioUtils.streamAudio(endpoint, model, voice, text, speed);
         });
      });
   }
}
