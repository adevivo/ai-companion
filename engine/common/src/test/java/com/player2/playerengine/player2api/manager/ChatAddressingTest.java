package com.player2.playerengine.player2api.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The word matching behind multiplayer chat routing; the lines are the real ones from holly. */
class ChatAddressingTest {

    @Test
    @DisplayName("the two lines said to StoneWolf_ on 2026-09-26 name that player")
    void namesAnotherPlayer() {
        assertTrue(ChatAddressing.mentionsPlayer("hiya stonewolf!", "StoneWolf_"));
        assertTrue(ChatAddressing.mentionsPlayer("StoneWolf_ hiya! :)", "StoneWolf_"));
        assertTrue(ChatAddressing.mentionsAnyPlayer("hiya stonewolf!", List.of("StoneWolf_")));
    }

    @Test
    @DisplayName("a username without its digits counts, but only as a whole word of 3+ letters")
    void digitlessShortForm() {
        assertTrue(ChatAddressing.mentionsPlayer("thanks dauk", "Dauk808"));
        assertFalse(ChatAddressing.mentionsPlayer("that's al for now", "Al42"));
        assertFalse(ChatAddressing.mentionsPlayer("daukish", "Dauk808"));
    }

    @Test
    @DisplayName("no prefix matching: ordinary words that start a name do not count")
    void noPrefixMatch() {
        assertFalse(ChatAddressing.mentionsPlayer("place a stone here", "StoneWolf_"));
        assertFalse(ChatAddressing.mentionsPlayer("stony terrain", "StoneWolf_"));
    }

    @Test
    @DisplayName("the companion's name anywhere, as a word, in any case")
    void mentionsCompanion() {
        assertTrue(ChatAddressing.mentionsPlayer("okay good job ava", "Ava"));
        assertTrue(ChatAddressing.mentions("thanks Ava!", "Ava"));
        assertTrue(ChatAddressing.mentions("hiya ava", "Ava"));
        assertFalse(ChatAddressing.mentions("that's lava", "Ava"));
        assertFalse(ChatAddressing.mentions("avalanche", "Ava"));
        assertFalse(ChatAddressing.mentions("anything", ""));
    }
}
