package com.player2.playerengine.util.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The allowed-container list must survive a save and load, and stay writable afterwards. The codec
 * decodes into immutable collections, so a list read back from disk that could not be added to would
 * work until the first restart and then throw on the first chest opened.
 */
class ChestPermissionsTest {
   @BeforeAll
   static void bootstrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   private static ChestPermissions roundTrip(ChestPermissions p) {
      JsonElement json = ChestPermissions.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow();
      return ChestPermissions.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
   }

   @Test
   void survivesSaveAndLoadPerOwner() {
      UUID alice = UUID.randomUUID();
      UUID bob = UUID.randomUUID();
      GlobalPos a1 = GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, -3));
      GlobalPos a2 = GlobalPos.of(Level.NETHER, new BlockPos(-5, 40, 7));
      GlobalPos b1 = GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 70, 0));
      ChestPermissions p = ChestPermissions.empty();
      p.allowedFor(alice).add(a1);
      p.allowedFor(alice).add(a2);
      p.allowedFor(bob).add(b1);

      ChestPermissions back = roundTrip(p);

      assertEquals(Set.of(a1, a2), back.allowedFor(alice));
      assertEquals(Set.of(b1), back.allowedFor(bob), "one owner's containers are not another's");
   }

   @Test
   void staysWritableAfterLoading() {
      UUID owner = UUID.randomUUID();
      ChestPermissions p = ChestPermissions.empty();
      p.allowedFor(owner).add(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));

      ChestPermissions back = roundTrip(p);

      assertTrue(back.allowedFor(owner).add(GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3))));
      assertTrue(back.allowedFor(UUID.randomUUID()).add(GlobalPos.of(Level.END, BlockPos.ZERO)));
   }
}
