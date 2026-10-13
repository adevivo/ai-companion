package com.player2.playerengine.player2api.status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses a list of sighted creatures into one short line per type.
 *
 * <p>Pure on purpose: a farm with forty chickens would otherwise put forty entries in every prompt,
 * and prompt tokens are paid for on every turn. Kept free of Minecraft types so it can be tested.
 */
public final class NearbyGrouping {
    private NearbyGrouping() {}

    /** One creature seen. {@code distance} is only used for ordering. */
    public record Sighting(String type, boolean baby, boolean leashed, double distance, int x, int y, int z) {}

    /**
     * {@code "cow x4 (2 baby), nearest at (1, 64, 2)"}, nearest-type first, at most {@code maxTypes}
     * entries, with an overflow note so the model knows the list was cut rather than complete.
     * Returns {@code emptyText} when there is nothing.
     */
    public static String describe(List<Sighting> sightings, int maxTypes, String emptyText) {
        if (sightings.isEmpty()) {
            return emptyText;
        }
        Map<String, List<Sighting>> byType = new LinkedHashMap<>();
        for (Sighting s : sightings) {
            byType.computeIfAbsent(s.type(), k -> new ArrayList<>()).add(s);
        }
        List<List<Sighting>> groups = new ArrayList<>(byType.values());
        groups.sort(Comparator.comparingDouble(NearbyGrouping::nearestDistance));

        List<String> lines = new ArrayList<>();
        for (List<Sighting> g : groups.subList(0, Math.min(maxTypes, groups.size()))) {
            Sighting nearest = g.stream().min(Comparator.comparingDouble(Sighting::distance)).get();
            long babies = g.stream().filter(Sighting::baby).count();
            long leashed = g.stream().filter(Sighting::leashed).count();
            StringBuilder sb = new StringBuilder(nearest.type()).append(" x").append(g.size());
            List<String> notes = new ArrayList<>();
            if (babies > 0) {
                notes.add(babies + " baby");
            }
            if (leashed > 0) {
                notes.add(leashed + " leashed");
            }
            if (!notes.isEmpty()) {
                sb.append(" (").append(String.join(", ", notes)).append(")");
            }
            sb.append(", nearest at (").append(nearest.x()).append(", ").append(nearest.y())
                    .append(", ").append(nearest.z()).append(")");
            lines.add("\"" + sb + "\"");
        }
        if (groups.size() > maxTypes) {
            lines.add("\"+" + (groups.size() - maxTypes) + " more types\"");
        }
        return "[" + String.join(",", lines) + "]";
    }

    private static double nearestDistance(List<Sighting> g) {
        return g.stream().mapToDouble(Sighting::distance).min().orElse(Double.MAX_VALUE);
    }
}
