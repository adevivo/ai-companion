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

package com.player2.playerengine;

import com.player2.playerengine.player2api.brain.BrainWire;
import com.player2.playerengine.player2api.manager.TTSManager;
import com.player2.playerengine.automaton.KeepName;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;

/**
 * Server-side init for the engine's agent half.
 *
 * <p>⚠️ This used to be a {@code ModInitializer} named in the old (pre-Architectury)
 * {@code fabric.mod.json}. The 1.21.11 port took its {@code fabric.mod.json} from upstream's
 * Architectury template, which never listed it — so for the whole port this class was <b>dead
 * code</b>: it compiled, it looked wired, and nothing ever called it. It is now invoked from
 * {@link PlayerEngine#onInitialize()}, which every loader runs, rather than depending on an
 * entrypoint list that a re-base can silently drop again. Keeping it out of the entrypoint list is
 * deliberate for the same reason.
 *
 * <p>What that cost, so nobody re-breaks it: {@link TTSManager#registerAckReceiver()} never ran, so
 * the server had no receiver on {@code tts_done}. The owner's client checks
 * {@code canServerReceive} before replying, found it false, and never sent the ack at all — which
 * meant a companion's speech lock was only ever released by its 60-second backstop. Since
 * {@code AgentConversationData.getPriority()} returns 0 while a companion is "speaking", every
 * queued message stalled for up to a minute. It read as network lag or a slow LLM; it was neither.
 */
@KeepName
public final class PlayerEngineServer {

   private PlayerEngineServer() {}

   /** Called from {@link PlayerEngine#onInitialize()} on every side and every loader. */
   public static void init() {
      // C2S, and it must run on BOTH sides. The server needs the receiver so an ack releases the
      // speech lock the moment the line finishes; the client needs the type registered so it can
      // send one at all — canServerReceive is false until this exists, and the client stays silent.
      TTSManager.registerAckReceiver();

      // S2C payload types: DEDICATED SERVER ONLY.
      //
      // Architectury learns a channel's type as a side effect of registering a RECEIVER for it, and
      // the receivers for both of these live on the client. A dedicated server never runs client
      // init, so without this its lookup misses and every outgoing packet is built with a null type
      // — silently, which is the whole reason the declaration exists.
      //
      // But declaring them on a client DOUBLE-registers: the client's own receiver registration
      // registers the same type, and Fabric's PayloadTypeRegistry throws on a duplicate id. That is
      // exactly the crash the mod half hit on 2026-08-23 (aicompanion:server_policy "is already
      // registered"), which killed the client entrypoint before the title screen.
      if (Platform.getEnvironment() == Env.SERVER) {
         TTSManager.registerSpeechChannel();
         BrainWire.registerServerToClientChannels();
      }
   }
}
