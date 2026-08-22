package com.player2.playerengine.util.serialization.gson;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.world.level.ChunkPos;

public class ChunkPosTypeAdapter extends AbstractVectorTypeAdapter<ChunkPos> {
    @Override
    protected Collection<String> getParts(ChunkPos value) {
        return Arrays.asList(String.valueOf(value.x), String.valueOf(value.z));
    }

    @Override
    protected ChunkPos fromParts(String[] parts) throws IOException {
        if (parts.length != 2) {
            throw new IOException("Invalid ChunkPos string: needs 2 components (x,z)");
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            return new ChunkPos(x, z);
        } catch (NumberFormatException e) {
            throw new IOException("Failed to parse ChunkPos components", e);
        }
    }
}