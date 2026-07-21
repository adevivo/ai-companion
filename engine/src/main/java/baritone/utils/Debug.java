package baritone.utils;

public class Debug {
   public static void logInternal(String message) {
      System.out.println("ALTO CLEF: " + message);
   }

   public static void logMessage(String message) {
      System.out.println("ALTO CLEF: " + message);
   }

   public static void logDebug(String message) {
      System.out.println("[DEBUG] ALTO CLEF: " + message);
   }

   public static void logError(String message) {
      System.err.println("[ERROR] ALTO CLEF: " + message);
   }
}
