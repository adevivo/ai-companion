package baritone.api.command.datatypes;

import baritone.api.IBaritone;
import baritone.api.command.argument.IArgConsumer;

public interface IDatatypeContext {
   IBaritone getBaritone();

   IArgConsumer getConsumer();
}
