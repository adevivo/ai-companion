package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;

public interface IDatatypePostFunction<T, O> {
   T apply(O var1) throws CommandException;
}
