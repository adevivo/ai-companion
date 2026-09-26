package com.player2.playerengine.player2api.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What is read aloud: short lines whole, long ones cut to whole sentences, lists cut at a word. */
class SpokenTextTest {
   @Test
   void shortMessagesAreSpokenWhole() {
      assertEquals("Got stone! Heading back to the house.", TTSManager.spokenText("Got stone! Heading back to the house."));
   }

   @Test
   void longMessagesKeepWholeSentencesOnly() {
      String msg = "The chest at (-6, 78, -128) has 10 oak logs and 26 free slots. "
         + "I can take some if you like, or put the spare cobblestone in there to make room for the glass we will need later on.";
      assertEquals("The chest at (-6, 78, -128) has 10 oak logs and 26 free slots.", TTSManager.spokenText(msg));
   }

   @Test
   void oneLongSentenceIsCutAtAWordAndTrailsOff() {
      // The 2026-09-25 case: one 250-char sentence that took 29 s to read.
      String msg = "My inventory: 64 glass, 2 string, 64 oak planks, wooden pickaxe, 64 dirt, 32 oak fence, 63 stone, "
         + "wooden axe, 64 sand, 32 oak stairs, 32 ladders, 130 cobblestone, 12 torches, 8 oak trapdoors, 4 oak doors and cooked beef";
      String spoken = TTSManager.spokenText(msg);

      assertTrue(spoken.length() <= TTSManager.MAX_SPOKEN_CHARS + 1, spoken);
      assertTrue(spoken.endsWith("…"), spoken);
      assertTrue(msg.startsWith(spoken.substring(0, spoken.length() - 1)), spoken);
      assertTrue(!spoken.contains(",…") && !spoken.contains(" …"), spoken);
   }

   @Test
   void decimalPointsAreNotSentenceEnds() {
      String msg = "Health is 4.3 out of 20 and dropping fast because I am stuck in powder snow near the cave entrance, "
         + "so I am breaking out of it now and will come straight back to you afterwards";
      assertTrue(TTSManager.spokenText(msg).endsWith("…"));
   }
}
