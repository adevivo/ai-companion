package com.player2.playerengine.util.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

/**
 * The blocks a body is in, at negative coordinates. Truncating with (int) put the answer one block
 * toward zero on each negative axis, and a companion building at x≈-3, z≈-131 was walled in and
 * suffocated because the body check looked at the wrong cells.
 */
class WorldHelperBlocksTouchingTest {
   private static Set<BlockPos> touching(AABB box) {
      Set<BlockPos> out = new HashSet<>();
      for (BlockPos pos : WorldHelper.getBlocksTouchingBox(box)) {
         out.add(pos.immutable());
      }
      return out;
   }

   @Test
   void negativeCoordinatesAreTheCellsTheBodyIsActuallyIn() {
      // The companion's box when it died: centred on (-3.50, 77.00, -131.24), 0.6 wide, 1.8 tall.
      AABB body = new AABB(-3.8, 77.0, -131.54, -3.2, 78.8, -130.94);

      Set<BlockPos> expected = Set.of(
         new BlockPos(-4, 77, -132), new BlockPos(-4, 77, -131),
         new BlockPos(-4, 78, -132), new BlockPos(-4, 78, -131));
      assertEquals(expected, touching(body));
   }

   @Test
   void positiveCoordinatesAreUnchanged() {
      AABB body = new AABB(3.2, 64.0, 10.2, 3.8, 65.8, 10.8);

      assertEquals(Set.of(new BlockPos(3, 64, 10), new BlockPos(3, 65, 10)), touching(body));
   }
}
