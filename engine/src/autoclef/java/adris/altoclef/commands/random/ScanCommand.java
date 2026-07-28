package adris.altoclef.commands.random;

import adris.altoclef.AltoClefController;
import adris.altoclef.commands.BlockScanner;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.util.helpers.FuzzySearchHelper;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

   private static Optional<ResourceLocation> toId(String name) {
      String normalized = name.toLowerCase().trim().replace(' ', '_');
      return Optional.ofNullable(ResourceLocation.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      String blockStr = parser.get(String.class);
      Optional<Block> block = resolveBlock(blockStr);
      if (block.isEmpty()) {
         // Naming a mob is the failure the models actually make, and the generic "did you mean" reply
         // sent them round the same loop again. Say what went wrong and where the answer already is.
         if (isEntityName(blockStr)) {
            mod.log(
               "\""
                  + blockStr
                  + "\" is a mob, not a block — scan only finds blocks. Nearby mobs are already listed in your world"
                  + " status, so you do not need a command to find them. Use `attack "
                  + blockStr.toLowerCase().trim()
                  + " 1` to go after one."
            );
         } else {
            List<String> allBlockNames = BuiltInRegistries.BLOCK.keySet().stream().map(ResourceLocation::getPath).toList();
            String closest = FuzzySearchHelper.getClosestMatchMinecraftItems(blockStr, allBlockNames);
            mod.log(
               "Block named: \""
                  + blockStr
                  + "\" not a valid block. Perhaps the user meant \""
                  + closest
                  + "\"?"
                  + (blockStr.contains("log") ? " Can try 'log' as well" : "")
            );
         }

         this.finish();
      } else {
         BlockScanner blockScanner = mod.getBlockScanner();
         Optional<BlockPos> p = blockScanner.getNearestBlock(block.get(), mod.getPlayer().position());
         if (p.isPresent()) {
            mod.log("Closest " + blockStr + ": " + p.get().toString());
         } else {
            mod.log("No blocks of type " + blockStr + " found nearby.");
         }

         this.finish();
      }
   }
}
