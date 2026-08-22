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

package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.utils.DirUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public enum RelativeFile implements IDatatypePost<File, File> {
   INSTANCE;

   public File apply(IDatatypeContext ctx, File original) throws CommandException {
      if (original == null) {
         original = new File("./");
      }

      Path path;
      try {
         path = FileSystems.getDefault().getPath(ctx.getConsumer().getString());
      } catch (InvalidPathException var5) {
         throw new IllegalArgumentException("invalid path");
      }

      return getCanonicalFileUnchecked(original.toPath().resolve(path).toFile());
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      return Stream.empty();
   }

   private static File getCanonicalFileUnchecked(File file) {
      try {
         return file.getCanonicalFile();
      } catch (IOException var2) {
         throw new UncheckedIOException(var2);
      }
   }

   public static Stream<String> tabComplete(IArgConsumer consumer, File base0) throws CommandException {
      File base = getCanonicalFileUnchecked(base0);
      String currentPathStringThing = consumer.getString();
      Path currentPath = FileSystems.getDefault().getPath(currentPathStringThing);
      Path basePath = currentPath.isAbsolute() ? currentPath.getRoot() : base.toPath();
      boolean useParent = !currentPathStringThing.isEmpty() && !currentPathStringThing.endsWith(File.separator);
      File currentFile = currentPath.isAbsolute() ? currentPath.toFile() : new File(base, currentPathStringThing);
      return Stream.of(Objects.requireNonNull(getCanonicalFileUnchecked(useParent ? currentFile.getParentFile() : currentFile).listFiles()))
         .map(f -> (currentPath.isAbsolute() ? f : basePath.relativize(f.toPath()).toString()) + (f.isDirectory() ? File.separator : ""))
         .filter(s -> s.toLowerCase(Locale.US).startsWith(currentPathStringThing.toLowerCase(Locale.US)))
         .filter(s -> !s.contains(" "));
   }

   public static File gameDir() {
      File gameDir = DirUtil.getGameDir().toFile().getAbsoluteFile();
      return gameDir.getName().equals(".") ? gameDir.getParentFile() : gameDir;
   }
}
