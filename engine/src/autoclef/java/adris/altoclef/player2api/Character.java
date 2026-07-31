
package adris.altoclef.player2api;
import java.util.Arrays;

public record Character(String name, String shortName, String greetingInfo, String description, String skinURL,
      String[] voiceIds, String persona) {

   /**
    * This character's own persona/personality block, injected into its system prompt where the
    * {@code {{persona}}} placeholder sits.
    *
    * <p>Per-character rather than the global {@link Prompts#persona} because a roster of companions
    * is otherwise a roster of clones: identity used to come from one static string, so two spawned
    * companions shared a name, a description and a personality and were indistinguishable in chat.
    * Blank falls back to the global, so a single-companion config keeps behaving exactly as before.
    */
   public Character {
      persona = persona == null ? "" : persona;
   }

   /**
    * Returns a formatted string representation of the Character object.
    *
    * @return A string containing character details.
    */
   @Override
   public String toString() {
      return String.format(
            "Character{name='%s', shortName='%s', greeting='%s', voiceIds=%s}",
            name,
            shortName,
            greetingInfo,
            Arrays.toString(voiceIds));
   }
}
