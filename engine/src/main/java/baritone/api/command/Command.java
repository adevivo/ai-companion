package baritone.api.command;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Command implements ICommand {
   protected final List<String> names;

   protected Command(String... names) {
      this.names = Collections.unmodifiableList(Stream.of(names).map(s -> s.toLowerCase(Locale.US)).collect(Collectors.toList()));
   }

   @Override
   public final List<String> getNames() {
      return this.names;
   }
}
