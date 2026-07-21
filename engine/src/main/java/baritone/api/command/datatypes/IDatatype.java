package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;
import java.util.stream.Stream;

public interface IDatatype {
   Stream<String> tabComplete(IDatatypeContext var1) throws CommandException;
}
