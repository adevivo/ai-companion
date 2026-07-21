package baritone.api.command;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public interface ICommand {
   void execute(CommandSourceStack var1, String var2, IArgConsumer var3, IBaritone var4) throws CommandException;

   Stream<String> tabComplete(String var1, IArgConsumer var2) throws CommandException;

   String getShortDesc();

   List<String> getLongDesc();

   List<String> getNames();

   default boolean hiddenFromHelp() {
      return false;
   }

   default void logDirect(CommandSourceStack source, Component... components) {
      source.sendSuccess(() -> {
         MutableComponent component = Component.literal("");
         component.append(BaritoneAPI.getPrefix());
         component.append(Component.literal(" "));

         for (Component t : components) {
            component.append(t);
         }

         return component;
      }, false);
   }

   default void logDirect(CommandSourceStack source, String message, ChatFormatting color) {
      Stream.of(message.split("\n")).forEach(line -> {
         MutableComponent component = Component.literal(line.replace("\t", "    "));
         component.setStyle(component.getStyle().applyFormat(color));
         this.logDirect(source, component);
      });
   }

   default void logDirect(CommandSourceStack source, String message) {
      this.logDirect(source, message, ChatFormatting.GRAY);
   }
}
