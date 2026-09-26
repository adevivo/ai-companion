package com.player2.playerengine.tasks.construction.build_structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The tags are how the turn model tells the build what the player meant. The player's own words
 * never reach this code, so these must be exact, and removing them must leave the description that
 * the plan cache is keyed on unchanged.
 */
class BuildTagsTest {
   private static final String HOUSE = "a small cobblestone house. Build at position (5, 77, -135)";

   @Test
   void tagsAreFoundInAnyCase() {
      assertTrue(BuildStructureTask.hasTag(HOUSE + " [Use Inventory]", BuildStructureTask.TAG_USE_INVENTORY));
      assertTrue(BuildStructureTask.hasTag(HOUSE + " [gather ok]", BuildStructureTask.TAG_GATHER_OK));
      assertFalse(BuildStructureTask.hasTag(HOUSE, BuildStructureTask.TAG_USE_INVENTORY));
      assertFalse(BuildStructureTask.hasTag(null, BuildStructureTask.TAG_GATHER_OK));
   }

   @Test
   void strippingLeavesTheSameCacheKey() {
      assertEquals(HOUSE, BuildStructureTask.stripTags(HOUSE + " [gather ok]"),
         "asking again with [gather ok] must find the plan it asked about");
      assertEquals(HOUSE, BuildStructureTask.stripTags(HOUSE + " [USE INVENTORY]"));
      assertEquals(HOUSE, BuildStructureTask.stripTags(HOUSE));
   }

   @Test
   void onlyTheTagsAreRemoved() {
      String withBrackets = "a tower with [three] floors. Build at position (0, 64, 0)";
      assertEquals(withBrackets, BuildStructureTask.stripTags(withBrackets + " [use inventory]"));
   }
}
