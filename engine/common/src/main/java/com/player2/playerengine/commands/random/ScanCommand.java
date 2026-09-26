package com.player2.playerengine.commands.random;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.BlockScanner;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.util.helpers.FuzzySearchHelper;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public class ScanCommand extends Command {
   public ScanCommand() throws CommandException {
      super("scan", "Locates the nearest BLOCK. Never finds mobs or players.", new Arg<>(String.class, "block", "DIRT", 0));
   }

   /**
    * Resolve a user-supplied block name through the block registry.
    *
    * <p>This used to reflect over {@code Blocks.class.getDeclaredFields()} and match the Java field
    * name. Under Fabric the Minecraft classes are intermediary-mapped at runtime, so those names are
    * {@code field_9975}, {@code field_10102}, … — meaning the lookup failed for <em>every</em> input,
    * and the "did you mean" suggestion offered an intermediary field name back to the caller. Registry
    * ids are the same at dev time and at runtime.
    */
   private static Optional<Block> resolveBlock(String name) {
      // DefaultedMappedRegistry overrides getOptional to bypass its air default, so an unknown id is
      // reported as empty rather than silently resolving to AIR.
      return toId(name).flatMap(BuiltInRegistries.BLOCK::getOptional);
   }

   private static boolean isEntityName(String name) {
      return toId(name).flatMap(id -> EntityType.byString(id.toString())).isPresent();
   }

   private static Optional<Identifier> toId(String name) {
      String normalized = name.toLowerCase().trim().replace(' ', '_');
      return Optional.ofNullable(Identifier.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized));
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String blockStr = parser.get(String.class);
      Optional<Block> block = resolveBlock(blockStr);
      if (block.isEmpty()) {
         // Naming a mob is the failure the models actually make, and the generic "did you mean" reply
         // sent them round the same loop again. Say what went wrong and where the answer already is.
         // Failures go to the agent, not stdout: mod.log() never reached the model, so `scan` answered
         // nothing at all, found or not. Kept out of chat (null), as the owner did not ask.
         if (isEntityName(blockStr)) {
            mod.logAgentNotice(
               "\""
                  + blockStr
                  + "\" is a mob, not a block — scan only finds blocks. Nearby mobs are already listed in your world"
                  + " status, so you do not need a command to find them. Use `attack "
                  + blockStr.toLowerCase().trim()
                  + " 1` to go after one.",
               null
            );
         } else {
            List<String> allBlockNames = BuiltInRegistries.BLOCK.keySet().stream().map(Identifier::getPath).toList();
            String closest = FuzzySearchHelper.getClosestMatchMinecraftItems(blockStr, allBlockNames);
            mod.logAgentNotice(
               "Block named: \""
                  + blockStr
                  + "\" not a valid block. Perhaps the user meant \""
                  + closest
                  + "\"?"
                  + (blockStr.contains("log") ? " Can try 'log' as well" : ""),
               null
            );
         }

         this.finish();
      } else {
         BlockScanner blockScanner = mod.getBlockScanner();
         Optional<BlockPos> p = blockScanner.getNearestBlock(block.get(), mod.getPlayer().position());
         if (p.isPresent()) {
            mod.reportCommandResult("Closest " + blockStr + ": (" + p.get().toShortString() + ").");
         } else {
            mod.reportCommandResult("No blocks of type " + blockStr + " found nearby.");
         }

         this.finish();
      }
   }
}
