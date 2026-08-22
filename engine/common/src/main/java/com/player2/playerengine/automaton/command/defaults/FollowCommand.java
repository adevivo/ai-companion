/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.KeepName;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.datatypes.EntityClassById;
import com.player2.playerengine.automaton.api.command.datatypes.IDatatypeFor;
import com.player2.playerengine.automaton.api.command.datatypes.NearbyPlayer;
import com.player2.playerengine.automaton.api.command.exception.CommandErrorMessageException;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class FollowCommand extends Command {
   public FollowCommand() {
      super("follow");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMin(1);
      List<Entity> entities = new ArrayList<>();
      List<EntityType<?>> classes = new ArrayList<>();
      FollowCommand.FollowGroup group;
      if (args.hasExactlyOne()) {
         baritone.getFollowProcess().follow((group = args.getEnum(FollowCommand.FollowGroup.class)).filter);
      } else {
         args.requireMin(2);
         group = null;
         FollowCommand.FollowList list = args.getEnum(FollowCommand.FollowList.class);

         while (args.hasAny()) {
            Object gotten = args.getDatatypeFor(list.datatype);
            if (gotten instanceof EntityType) {
               classes.add((EntityType<?>)gotten);
            } else if (gotten != null) {
               entities.add((Entity)gotten);
            }
         }

         baritone.getFollowProcess().follow(classes.isEmpty() ? entities::contains : e -> classes.stream().anyMatch(c -> e.getType().equals(c)));
      }

      if (group != null) {
         this.logDirect(source, String.format("Following all %s", group.name().toLowerCase(Locale.US)));
      } else if (classes.isEmpty()) {
         if (entities.isEmpty()) {
            throw new FollowCommand.NoEntitiesException();
         }

         this.logDirect(source, "Following these entities:");
         entities.stream().<String>map(Entity::toString).forEach(message -> this.logDirect(source, message));
      } else {
         this.logDirect(source, "Following these types of entities:");
         classes.stream()
            .map(BuiltInRegistries.ENTITY_TYPE::getKey)
            .map(Objects::requireNonNull)
            .<String>map(Identifier::toString)
            .forEach(message -> this.logDirect(source, message));
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      if (args.hasExactlyOne()) {
         return new TabCompleteHelper().append(FollowCommand.FollowGroup.class).append(FollowCommand.FollowList.class).filterPrefix(args.getString()).stream();
      } else {
         IDatatypeFor<?> followType;
         try {
            followType = args.getEnum(FollowCommand.FollowList.class).datatype;
         } catch (NullPointerException var5) {
            return Stream.empty();
         }

         while (args.has(2)) {
            if (args.peekDatatypeOrNull(followType) == null) {
               return Stream.empty();
            }

            args.get();
         }

         return args.tabCompleteDatatype(followType);
      }
   }

   @Override
   public String getShortDesc() {
      return "Follow entity things";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The follow command makes an entity follow other entities of certain kinds.",
         "",
         "Usage:",
         "> follow entities - Follows all entities.",
         "> follow entity <entity1> <entity2> <...> - Follow certain entities (for example 'skeleton', 'horse' etc.)",
         "> follow players - Follow players",
         "> follow player <username1> <username2> <...> - Follow certain players"
      );
   }

   @KeepName
   private static enum FollowGroup {
      ENTITIES(LivingEntity.class::isInstance),
      PLAYERS(Player.class::isInstance);

      final Predicate<Entity> filter;

      private FollowGroup(Predicate<Entity> filter) {
         this.filter = filter;
      }
   }

   @KeepName
   private static enum FollowList {
      ENTITY(EntityClassById.INSTANCE),
      PLAYER(NearbyPlayer.INSTANCE);

      final IDatatypeFor<?> datatype;

      private FollowList(IDatatypeFor<?> datatype) {
         this.datatype = datatype;
      }
   }

   public static class NoEntitiesException extends CommandErrorMessageException {
      protected NoEntitiesException() {
         super("No valid entities in range!");
      }
   }
}
