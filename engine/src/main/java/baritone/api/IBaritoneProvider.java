package baritone.api;

import baritone.api.cache.IWorldScanner;
import baritone.api.command.ICommandSystem;
import baritone.api.schematic.ISchematicSystem;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;

public interface IBaritoneProvider {
   IBaritone getBaritone(LivingEntity var1);

   IWorldScanner getWorldScanner();

   ICommandSystem getCommandSystem();

   ISchematicSystem getSchematicSystem();

   Settings getGlobalSettings();

   <E extends LivingEntity> Function<E, IBaritone> componentFactory();
}
