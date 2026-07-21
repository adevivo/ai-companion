package baritone.api.command.helpers;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidTypeException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;

public class Paginator<E> {
   private final CommandSourceStack source;
   public final List<E> entries;
   public int pageSize = 8;
   public int page = 1;

   public Paginator(CommandSourceStack source, List<E> entries) {
      this.source = source;
      this.entries = entries;
   }

   public Paginator<E> setPageSize(int pageSize) {
      this.pageSize = pageSize;
      return this;
   }

   public int getMaxPage() {
      return (this.entries.size() - 1) / this.pageSize + 1;
   }

   public boolean validPage(int page) {
      return page > 0 && page <= this.getMaxPage();
   }

   public Paginator<E> skipPages(int pages) {
      this.page += pages;
      return this;
   }

   public void display(Function<E, Component> transform, String commandPrefix) {
      int offset = (this.page - 1) * this.pageSize;

      for (int i = offset; i < offset + this.pageSize; i++) {
         if (i < this.entries.size()) {
            E entry = this.entries.get(i);
            this.source.sendSuccess(() -> transform.apply(entry), false);
         } else {
            this.source.sendSuccess(() -> Component.literal("--").withStyle(ChatFormatting.DARK_GRAY), false);
         }
      }

      boolean hasPrevPage = commandPrefix != null && this.validPage(this.page - 1);
      boolean hasNextPage = commandPrefix != null && this.validPage(this.page + 1);
      MutableComponent prevPageComponent = Component.literal("<<");
      if (hasPrevPage) {
         prevPageComponent.setStyle(
            prevPageComponent.getStyle()
               .withClickEvent(new ClickEvent(Action.RUN_COMMAND, String.format("%s %d", commandPrefix, this.page - 1)))
               .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view previous page")))
         );
      } else {
         prevPageComponent.setStyle(prevPageComponent.getStyle().applyFormat(ChatFormatting.DARK_GRAY));
      }

      MutableComponent nextPageComponent = Component.literal(">>");
      if (hasNextPage) {
         nextPageComponent.setStyle(
            nextPageComponent.getStyle()
               .withClickEvent(new ClickEvent(Action.RUN_COMMAND, String.format("%s %d", commandPrefix, this.page + 1)))
               .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view next page")))
         );
      } else {
         nextPageComponent.setStyle(nextPageComponent.getStyle().applyFormat(ChatFormatting.DARK_GRAY));
      }

      this.source.sendSuccess(() -> {
         MutableComponent pagerComponent = Component.literal("");
         pagerComponent.setStyle(pagerComponent.getStyle().applyFormat(ChatFormatting.GRAY));
         pagerComponent.append(prevPageComponent);
         pagerComponent.append(" | ");
         pagerComponent.append(nextPageComponent);
         pagerComponent.append(String.format(" %d/%d", this.page, this.getMaxPage()));
         return pagerComponent;
      }, false);
   }

   public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Runnable pre, Function<T, Component> transform, String commandPrefix) throws CommandException {
      int page = 1;
      consumer.requireMax(1);
      if (consumer.hasAny()) {
         page = consumer.getAs(Integer.class);
         if (!pagi.validPage(page)) {
            throw new CommandInvalidTypeException(consumer.consumed(), String.format("a valid page (1-%d)", pagi.getMaxPage()), consumer.consumed().getValue());
         }
      }

      pagi.skipPages(page - pagi.page);
      if (pre != null) {
         pre.run();
      }

      pagi.display(transform, commandPrefix);
   }

   public static <T> void paginate(
      IArgConsumer consumer, List<T> elems, Runnable pre, Function<T, Component> transform, String commandPrefix, CommandSourceStack source
   ) throws CommandException {
      paginate(consumer, new Paginator<>(source, elems), pre, transform, commandPrefix);
   }

   public static <T> void paginate(
      IArgConsumer consumer, T[] elems, Runnable pre, Function<T, Component> transform, String commandPrefix, CommandSourceStack source
   ) throws CommandException {
      paginate(consumer, Arrays.asList(elems), pre, transform, commandPrefix, source);
   }

   public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Function<T, Component> transform, String commandPrefix) throws CommandException {
      paginate(consumer, pagi, null, transform, commandPrefix);
   }

   public static <T> void paginate(IArgConsumer consumer, List<T> elems, Function<T, Component> transform, String commandPrefix, CommandSourceStack source) throws CommandException {
      paginate(consumer, new Paginator<>(source, elems), null, transform, commandPrefix);
   }

   public static <T> void paginate(IArgConsumer consumer, T[] elems, Function<T, Component> transform, String commandPrefix, CommandSourceStack source) throws CommandException {
      paginate(consumer, Arrays.asList(elems), null, transform, commandPrefix, source);
   }

   public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Runnable pre, Function<T, Component> transform) throws CommandException {
      paginate(consumer, pagi, pre, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, List<T> elems, Runnable pre, Function<T, Component> transform, CommandSourceStack source) throws CommandException {
      paginate(consumer, new Paginator<>(source, elems), pre, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, T[] elems, Runnable pre, Function<T, Component> transform, CommandSourceStack source) throws CommandException {
      paginate(consumer, Arrays.asList(elems), pre, transform, null, source);
   }

   public static <T> void paginate(IArgConsumer consumer, Paginator<T> pagi, Function<T, Component> transform) throws CommandException {
      paginate(consumer, pagi, null, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, List<T> elems, Function<T, Component> transform, CommandSourceStack source) throws CommandException {
      paginate(consumer, new Paginator<>(source, elems), null, transform, null);
   }

   public static <T> void paginate(IArgConsumer consumer, T[] elems, Function<T, Component> transform, CommandSourceStack source) throws CommandException {
      paginate(consumer, Arrays.asList(elems), null, transform, null, source);
   }
}
