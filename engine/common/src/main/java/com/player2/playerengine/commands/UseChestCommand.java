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
         "Your owner lets you use the chest, barrel or shulker box they are looking at or standing next to. Run it ONLY when your owner tells you to use that container, never on your own. `chests`, `withdraw` and `deposit` only use containers your owner has allowed.",
         true
      );
   }

   public static UseChestCommand forget() throws CommandException {
      return new UseChestCommand(
         "forgetchest",
         "Stop using the chest, barrel or shulker box your owner is looking at or standing next to, when they tell you to leave it alone.",
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
