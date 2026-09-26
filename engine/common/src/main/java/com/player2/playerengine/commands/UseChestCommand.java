package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.util.helpers.ChestPermissions;
import com.player2.playerengine.util.helpers.ContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * `usechest` / `forgetchest`: add or remove the container the owner is at from the ones this owner's
 * companions may use. See {@link ChestPermissions}.
 */
public class UseChestCommand extends Command {
   private final boolean allow;

   private UseChestCommand(String name, String description, boolean allow) throws CommandException {
      super(name, description);
      this.allow = allow;
   }

   public static UseChestCommand use() throws CommandException {
      return new UseChestCommand(
         "usechest",
         "Allow the container your owner is at. ONLY when they tell you to use it.",
         true
      );
   }

   public static UseChestCommand forget() throws CommandException {
      return new UseChestCommand(
         "forgetchest",
         "Stop using the container your owner is at, when they say so.",
         false
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      Player owner = mod.getOwner();
      if (owner == null) {
         throw new CommandException("Your owner is not here, so there is no container to " + (this.allow ? "use" : "forget") + ".");
      }
      BlockPos pos = ChestPermissions.containerAtOwner(owner).orElseThrow(() -> new CommandException(
         "Your owner is not looking at or standing next to a chest, barrel or shulker box. Ask them to stand at the one they mean."
      ));
      String what = ContainerAccess.describe(owner.level(), pos) + " at (" + pos.toShortString() + ")";
      if (this.allow) {
         boolean added = ChestPermissions.allow(owner.level(), owner.getUUID(), pos);
         mod.reportCommandResult(added ? "You may now use the " + what + "." : "You could already use the " + what + ".");
      } else {
         boolean removed = ChestPermissions.forget(owner.level(), owner.getUUID(), pos);
         mod.reportCommandResult(removed
            ? "You will no longer use the " + what + ". Opening it themselves lets you use it again."
            : "You were not using the " + what + ".");
      }
      this.finish();
   }
}
