package adris.altoclef.tasks.construction;

import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import baritone.api.process.IBuilderProcess;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public class DestroyBlockTask extends Task implements ITaskRequiresGrounded {
   private final BlockPos pos;
   private boolean isClear;

   public DestroyBlockTask(BlockPos pos) {
      this.pos = pos;
   }

   @Override
   protected void onStart() {
      this.isClear = false;
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();
      builder.clearArea(this.pos, this.pos);
   }

   @Override
   protected Task onTick() {
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();
      if (!builder.isActive()) {
         this.isClear = true;
         return null;
      } else {
         this.setDebugState("Automatone is breaking the block.");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      IBuilderProcess builder = this.controller.getBaritone().getBuilderProcess();
      if (builder.isActive()) {
         builder.onLostControl();
      }
   }

   @Override
   public boolean isFinished() {
      return this.isClear || this.controller.getWorld().isEmptyBlock(this.pos);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof DestroyBlockTask task ? Objects.equals(task.pos, this.pos) : false;
   }

   @Override
   protected String toDebugString() {
      return "Destroying block at " + this.pos.toShortString();
   }
}
