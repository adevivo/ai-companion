package baritone.api.command.datatypes;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.utils.DirUtil;
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
