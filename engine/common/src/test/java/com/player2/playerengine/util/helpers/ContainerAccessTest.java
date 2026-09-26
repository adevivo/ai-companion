package com.player2.playerengine.util.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Item moves must conserve items. Both container tasks this replaced duplicated them, so every test
 * checks the total across both sides, not only the side that was asked about.
 */
class ContainerAccessTest {
   @BeforeAll
   static void bootstrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   private static int total(SimpleContainer chest, LivingEntityInventory inv, Item item) {
      int n = ContainerAccess.count(chest, i -> i == item);
      for (ItemStack s : inv.main) {
         if (s.is(item)) {
            n += s.getCount();
         }
      }
      return n;
   }

   @Test
   void takeMovesExactlyTheCountAndConserves() {
      SimpleContainer chest = new SimpleContainer(27);
      chest.setItem(0, new ItemStack(Items.WHITE_WOOL, 10));
      chest.setItem(5, new ItemStack(Items.WHITE_WOOL, 10));
      LivingEntityInventory inv = new LivingEntityInventory(null);

      int moved = ContainerAccess.take(chest, inv, i -> i == Items.WHITE_WOOL, 15);

      assertEquals(15, moved);
      assertEquals(5, ContainerAccess.count(chest, i -> i == Items.WHITE_WOOL));
      assertEquals(20, total(chest, inv, Items.WHITE_WOOL));
   }

   @Test
   void takeStopsWhenTheInventoryIsFullWithoutLosingItems() {
      SimpleContainer chest = new SimpleContainer(27);
      chest.setItem(0, new ItemStack(Items.DIRT, 64));
      LivingEntityInventory inv = new LivingEntityInventory(null);
      for (int i = 0; i < inv.main.size(); i++) {
         inv.main.set(i, new ItemStack(Items.STONE, 64));
      }
      inv.main.set(0, new ItemStack(Items.DIRT, 60)); // room for exactly 4

      int moved = ContainerAccess.take(chest, inv, i -> i == Items.DIRT, 64);

      assertEquals(4, moved);
      assertEquals(124, total(chest, inv, Items.DIRT));
   }

   @Test
   void putOntoAPartialStackDoesNotDuplicate() {
      SimpleContainer chest = new SimpleContainer(27);
      chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
      LivingEntityInventory inv = new LivingEntityInventory(null);
      inv.main.set(3, new ItemStack(Items.DIAMOND, 7));

      int moved = ContainerAccess.put(inv, chest, s -> s.is(Items.DIAMOND), 2);

      assertEquals(2, moved);
      assertEquals(7, ContainerAccess.count(chest, i -> i == Items.DIAMOND));
      assertEquals(5, inv.main.get(3).getCount());
      assertEquals(12, total(chest, inv, Items.DIAMOND));
   }

   @Test
   void putStopsWhenTheContainerIsFull() {
      SimpleContainer chest = new SimpleContainer(1);
      chest.setItem(0, new ItemStack(Items.COBBLESTONE, 60));
      LivingEntityInventory inv = new LivingEntityInventory(null);
      inv.main.set(0, new ItemStack(Items.COBBLESTONE, 10));

      int moved = ContainerAccess.put(inv, chest, s -> s.is(Items.COBBLESTONE), 10);

      assertEquals(4, moved);
      assertEquals(70, total(chest, inv, Items.COBBLESTONE));
   }
}
