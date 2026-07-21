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

package adris.altoclef.player2api.manager;

import net.minecraft.nbt.CompoundTag;

public class HeartbeatManager {
    private static final HeartbeatManager INSTANCE = new HeartbeatManager();
    private CompoundTag tokensStored = new CompoundTag();

    private String makeKey(String username, String clientId) {
        return username + ":" + clientId;
    }

    public static boolean shouldHeartbeat(String username, String clientId){
        long now = System.nanoTime();
        return now - getLastTime(username, clientId) > 60_000_000_000L;
    }

    static long getLastTime(String username, String clientId) {
        return getInstance().tokensStored.getLong(getInstance().makeKey(username, clientId));
    }

    public static void storeHeartbeatTime(String username, String clientId) {
        getInstance().tokensStored.putLong(getInstance().makeKey(username, clientId), System.nanoTime());
    }

    private static HeartbeatManager getInstance() {
        return INSTANCE;
    }
}