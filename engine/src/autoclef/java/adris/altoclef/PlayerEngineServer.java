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
import baritone.KeepName;
import net.fabricmc.api.ModInitializer;

/**
 * Server-side init for the altoclef half of the engine.
 *
 * <p>Separate from {@link baritone.PlayerEngine} because that class lives in the {@code main} source
 * set, which cannot see {@code adris.altoclef} — the dependency runs the other way.
 */
@KeepName
public final class PlayerEngineServer implements ModInitializer {
   public void onInitialize() {
      // Companion speech plays on the owner's machine, so only the owner's client can say whether it
      // happened. It answers when the line finishes — or straight away when there is no Kokoro server
      // to play it — and that answer is what releases the companion's speech lock.
      TTSManager.registerAckReceiver();
   }
}
