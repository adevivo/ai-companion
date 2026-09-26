package com.player2.playerengine.tasks.container;

import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.ContainerAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;

/** `chests`: open each nearby container and write down what is in it. */
public class SurveyContainersTask extends VisitContainersTask {
   /** Containers checked per command — the rest are counted, not walked to. */
   private static final int MAX_CONTAINERS = 8;
   /** Item kinds listed per container before the remainder is summarised as "+N more kinds". */
   private static final int MAX_KINDS = 12;

   private final List<String> lines = new ArrayList<>();
   private int notChecked;

   @Override
   protected List<BlockPos> plan(List<BlockPos> nearestFirst) {
      this.lines.clear();
      this.notChecked = Math.max(0, nearestFirst.size() - MAX_CONTAINERS);
      return nearestFirst.subList(0, Math.min(MAX_CONTAINERS, nearestFirst.size()));
   }

   @Override
   protected boolean visit(BlockPos pos, Container container) {
      Map<String, Integer> contents = ContainerAccess.summarize(container);
      String what;
      if (contents.isEmpty()) {
         what = "empty";
      } else {
         List<String> parts = new ArrayList<>();
         contents.entrySet().stream().limit(MAX_KINDS).forEach(e -> parts.add(e.getValue() + " " + e.getKey()));
         if (contents.size() > MAX_KINDS) {
            parts.add("+" + (contents.size() - MAX_KINDS) + " more kinds");
         }
         what = String.join(", ", parts) + " (" + ContainerAccess.freeSlots(container) + " free slots)";
      }
      this.lines.add(ContainerAccess.describe(this.controller.getWorld(), pos) + " at (" + pos.toShortString() + "): " + what);
      return false;
   }

   /** The listing for the agent. */
   public String report() {
      if (this.lines.isEmpty() && this.unreachable.isEmpty() && this.notAllowed == 0) {
         return "There are no chests, barrels or shulker boxes within " + ContainerAccess.SEARCH_RADIUS + " blocks.";
      }
      StringBuilder out = new StringBuilder("Checked " + this.lines.size() + " container(s)");
      out.append(this.lines.isEmpty() ? "." : ": " + String.join("; ", this.lines) + ".");
      out.append(this.notes());
      if (this.notChecked > 0) {
         out.append(" ").append(this.notChecked).append(" more further away were not checked.");
      }
      out.append(" Use `withdraw <item> <count>` to take items out, or `deposit <item> <count>` to put them in.");
      return out.toString();
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SurveyContainersTask;
   }

   @Override
   protected String toDebugString() {
      return "Checking nearby containers";
   }
}
