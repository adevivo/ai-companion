package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForBlockOptionalMeta;
import baritone.api.command.datatypes.ForDirection;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.schematic.CompositeSchematic;
import baritone.api.schematic.FillSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.schematic.ReplaceSchematic;
import baritone.api.schematic.ShellSchematic;
import baritone.api.schematic.WallsSchematic;
import baritone.api.selection.ISelection;
import baritone.api.selection.ISelectionManager;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

public class SelCommand extends Command {
   private BetterBlockPos pos1 = null;

   public SelCommand() {
      super("sel", "selection", "s");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      SelCommand.Action action = SelCommand.Action.getByName(args.getString());
      if (action == null) {
         throw new CommandInvalidTypeException(args.consumed(), "an action");
      } else {
         ISelectionManager manager = ISelectionManager.KEY.get(baritone.getEntityContext().entity());
         if (action != SelCommand.Action.POS1 && action != SelCommand.Action.POS2) {
            if (action == SelCommand.Action.CLEAR) {
               args.requireMax(0);
               this.pos1 = null;
               this.logDirect(source, String.format("Removed %d selections", manager.removeAllSelections().length));
            } else if (action == SelCommand.Action.UNDO) {
               args.requireMax(0);
               if (this.pos1 != null) {
                  this.pos1 = null;
                  this.logDirect(source, "Undid pos1");
               } else {
                  ISelection[] selections = manager.getSelections();
                  if (selections.length < 1) {
                     throw new CommandInvalidStateException("Nothing to undo!");
                  }

                  this.pos1 = manager.removeSelection(selections[selections.length - 1]).pos1();
                  this.logDirect(source, "Undid pos2");
               }
            } else if (action != SelCommand.Action.SET
               && action != SelCommand.Action.WALLS
               && action != SelCommand.Action.SHELL
               && action != SelCommand.Action.CLEARAREA
               && action != SelCommand.Action.REPLACE) {
               if (action == SelCommand.Action.EXPAND || action == SelCommand.Action.CONTRACT || action == SelCommand.Action.SHIFT) {
                  args.requireExactly(3);
                  SelCommand.TransformTarget transformTarget = SelCommand.TransformTarget.getByName(args.getString());
                  if (transformTarget == null) {
                     throw new CommandInvalidStateException("Invalid transform type");
                  }

                  Direction direction = args.getDatatypeFor(ForDirection.INSTANCE);
                  int blocks = args.getAs(Integer.class);
                  ISelection[] selections = manager.getSelections();
                  if (selections.length < 1) {
                     throw new CommandInvalidStateException("No selections found");
                  }

                  selections = transformTarget.transform(selections);

                  for (ISelection selection : selections) {
                     if (action == SelCommand.Action.EXPAND) {
                        manager.expand(selection, direction, blocks);
                     } else if (action == SelCommand.Action.CONTRACT) {
                        manager.contract(selection, direction, blocks);
                     } else {
                        manager.shift(selection, direction, blocks);
                     }
                  }

                  this.logDirect(source, String.format("Transformed %d selections", selections.length));
               }
            } else {
               BlockOptionalMeta type = action == SelCommand.Action.CLEARAREA
                  ? new BlockOptionalMeta(baritone.getEntityContext().world(), Blocks.AIR)
                  : args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
               BlockOptionalMetaLookup replaces = null;
               if (action != SelCommand.Action.REPLACE) {
                  args.requireMax(0);
               } else {
                  args.requireMin(1);
                  List<BlockOptionalMeta> replacesList = new ArrayList<>();
                  replacesList.add(type);

                  while (args.has(2)) {
                     replacesList.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
                  }

                  type = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
                  replaces = new BlockOptionalMetaLookup(replacesList.toArray(new BlockOptionalMeta[0]));
               }

               ISelection[] selections = manager.getSelections();
               if (selections.length == 0) {
                  throw new CommandInvalidStateException("No selections");
               }

               BetterBlockPos origin = selections[0].min();
               CompositeSchematic composite = new CompositeSchematic(0, 0, 0);

               for (ISelection selectionx : selections) {
                  BetterBlockPos min = selectionx.min();
                  origin = new BetterBlockPos(Math.min(origin.x, min.x), Math.min(origin.y, min.y), Math.min(origin.z, min.z));
               }

               for (ISelection selectionx : selections) {
                  Vec3i size = selectionx.size();
                  BetterBlockPos min = selectionx.min();
                  ISchematic schematic = new FillSchematic(size.getX(), size.getY(), size.getZ(), type);
                  if (action == SelCommand.Action.WALLS) {
                     schematic = new WallsSchematic(schematic);
                  } else if (action == SelCommand.Action.SHELL) {
                     schematic = new ShellSchematic(schematic);
                  } else if (action == SelCommand.Action.REPLACE) {
                     schematic = new ReplaceSchematic(schematic, replaces);
                  }

                  composite.put(schematic, min.x - origin.x, min.y - origin.y, min.z - origin.z);
               }

               baritone.getBuilderProcess().build("Fill", composite, origin);
               this.logDirect(source, "Filling now");
            }
         } else {
            if (action == SelCommand.Action.POS2 && this.pos1 == null) {
               throw new CommandInvalidStateException("Set pos1 first before using pos2");
            }

            LivingEntity entity = baritone.getEntityContext().entity();
            BetterBlockPos playerPos = entity instanceof ServerPlayer && ((ServerPlayer)entity).getCamera() != null
               ? BetterBlockPos.from(((ServerPlayer)entity).getCamera().blockPosition())
               : baritone.getEntityContext().feetPos();
            BetterBlockPos pos = args.hasAny() ? args.getDatatypePost(RelativeBlockPos.INSTANCE, playerPos) : playerPos;
            args.requireMax(0);
            if (action == SelCommand.Action.POS1) {
               this.pos1 = pos;
               this.logDirect(source, "Position 1 has been set");
            } else {
               manager.addSelection(this.pos1, pos);
               this.pos1 = null;
               this.logDirect(source, "Selection added");
            }
         }
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      if (args.hasExactlyOne()) {
         return new TabCompleteHelper().append(SelCommand.Action.getAllNames()).filterPrefix(args.getString()).sortAlphabetically().stream();
      } else {
         SelCommand.Action action = SelCommand.Action.getByName(args.getString());
         if (action != null) {
            if (action != SelCommand.Action.POS1 && action != SelCommand.Action.POS2) {
               if (action != SelCommand.Action.SET
                  && action != SelCommand.Action.WALLS
                  && action != SelCommand.Action.CLEARAREA
                  && action != SelCommand.Action.REPLACE) {
                  if (action == SelCommand.Action.EXPAND || action == SelCommand.Action.CONTRACT || action == SelCommand.Action.SHIFT) {
                     if (args.hasExactlyOne()) {
                        return new TabCompleteHelper()
                           .append(SelCommand.TransformTarget.getAllNames())
                           .filterPrefix(args.getString())
                           .sortAlphabetically()
                           .stream();
                     }

                     SelCommand.TransformTarget target = SelCommand.TransformTarget.getByName(args.getString());
                     if (target != null && args.hasExactlyOne()) {
                        return args.tabCompleteDatatype(ForDirection.INSTANCE);
                     }
                  }
               } else if (args.hasExactlyOne() || action == SelCommand.Action.REPLACE) {
                  while (args.has(2)) {
                     args.get();
                  }

                  return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
               }
            } else if (args.hasAtMost(3)) {
               return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
            }
         }

         return Stream.empty();
      }
   }

   @Override
   public String getShortDesc() {
      return "WorldEdit-like commands";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The sel command allows you to manipulate Baritone's selections, similarly to WorldEdit.",
         "",
         "Using these selections, you can clear areas, fill them with blocks, or something else.",
         "",
         "The expand/contract/shift commands use a kind of selector to choose which selections to target. Supported ones are a/all, n/newest, and o/oldest.",
         "",
         "Usage:",
         "> sel pos1/p1/1 - Set position 1 to your current position.",
         "> sel pos1/p1/1 <x> <y> <z> - Set position 1 to a relative position.",
         "> sel pos2/p2/2 - Set position 2 to your current position.",
         "> sel pos2/p2/2 <x> <y> <z> - Set position 2 to a relative position.",
         "",
         "> sel clear/c - Clear the selection.",
         "> sel undo/u - Undo the last action (setting positions, creating selections, etc.)",
         "> sel set/fill/s/f [block] - Completely fill all selections with a block.",
         "> sel walls/w [block] - Fill in the walls of the selection with a specified block.",
         "> sel shell/shl [block] - The same as walls, but fills in a ceiling and floor too.",
         "> sel cleararea/ca - Basically 'set air'.",
         "> sel replace/r <blocks...> <with> - Replaces blocks with another block.",
         "",
         "> sel expand <target> <direction> <blocks> - Expand the targets.",
         "> sel contract <target> <direction> <blocks> - Contract the targets.",
         "> sel shift <target> <direction> <blocks> - Shift the targets (does not resize)."
      );
   }

   static enum Action {
      POS1("pos1", "p1", "1"),
      POS2("pos2", "p2", "2"),
      CLEAR("clear", "c"),
      UNDO("undo", "u"),
      SET("set", "fill", "s", "f"),
      WALLS("walls", "w"),
      SHELL("shell", "shl"),
      CLEARAREA("cleararea", "ca"),
      REPLACE("replace", "r"),
      EXPAND("expand", "ex"),
      CONTRACT("contract", "ct"),
      SHIFT("shift", "sh");

      private final String[] names;

      private Action(String... names) {
         this.names = names;
      }

      public static SelCommand.Action getByName(String name) {
         for (SelCommand.Action action : values()) {
            for (String alias : action.names) {
               if (alias.equalsIgnoreCase(name)) {
                  return action;
               }
            }
         }

         return null;
      }

      public static String[] getAllNames() {
         Set<String> names = new HashSet<>();

         for (SelCommand.Action action : values()) {
            names.addAll(Arrays.asList(action.names));
         }

         return names.toArray(new String[0]);
      }
   }

   static enum TransformTarget {
      ALL(sels -> sels, "all", "a"),
      NEWEST(sels -> new ISelection[]{sels[sels.length - 1]}, "newest", "n"),
      OLDEST(sels -> new ISelection[]{sels[0]}, "oldest", "o");

      private final Function<ISelection[], ISelection[]> transform;
      private final String[] names;

      private TransformTarget(Function<ISelection[], ISelection[]> transform, String... names) {
         this.transform = transform;
         this.names = names;
      }

      public ISelection[] transform(ISelection[] selections) {
         return this.transform.apply(selections);
      }

      public static SelCommand.TransformTarget getByName(String name) {
         for (SelCommand.TransformTarget target : values()) {
            for (String alias : target.names) {
               if (alias.equalsIgnoreCase(name)) {
                  return target;
               }
            }
         }

         return null;
      }

      public static String[] getAllNames() {
         Set<String> names = new HashSet<>();

         for (SelCommand.TransformTarget target : values()) {
            names.addAll(Arrays.asList(target.names));
         }

         return names.toArray(new String[0]);
      }
   }
}
