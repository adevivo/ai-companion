package com.player2.playerengine.player2api.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.player2.playerengine.player2api.status.NearbyGrouping.Sighting;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NearbyGroupingTest {

    @Test
    void emptyReturnsTheEmptyText() {
        assertEquals("none", NearbyGrouping.describe(List.of(), 8, "none"));
    }

    @Test
    void groupsByTypeWithBabiesAndNearestPosition() {
        List<Sighting> s = List.of(
                new Sighting("cow", false, false, 10, 10, 64, 10),
                new Sighting("cow", true, false, 3, 1, 64, 2),
                new Sighting("cow", true, true, 7, 5, 64, 5),
                new Sighting("cow", false, false, 20, 20, 64, 20));
        assertEquals("[\"cow x4 (2 baby, 1 leashed), nearest at (1, 64, 2)\"]",
                NearbyGrouping.describe(s, 8, "none"));
    }

    @Test
    void sortsNearestTypeFirstAndCapsWithOverflowNote() {
        List<Sighting> s = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            s.add(new Sighting("type" + i, false, false, 10 - i, i, 0, 0));
        }
        String out = NearbyGrouping.describe(s, 8, "none");
        assertTrue(out.startsWith("[\"type9 x1"), out);
        assertTrue(out.contains("type2 x1"), out);
        assertFalse(out.contains("type1 x1"), out);
        assertTrue(out.endsWith("\"+2 more types\"]"), out);
    }
}
