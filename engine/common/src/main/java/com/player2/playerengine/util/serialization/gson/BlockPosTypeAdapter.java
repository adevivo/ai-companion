package com.player2.playerengine.util.serialization.gson;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.core.BlockPos;

public class BlockPosTypeAdapter extends AbstractVectorTypeAdapter<BlockPos> {

    @Override
    protected Collection<String> getParts(BlockPos value) {
        return Arrays.asList(String.valueOf(value.getX()), String.valueOf(value.getY()), String.valueOf(value.getZ()));
    }

    @Override
    protected BlockPos fromParts(String[] parts) throws IOException {
        if (parts.length != 3) {
            throw new IOException("Invalid BlockPos string: needs 3 components (x,y,z)");
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            throw new IOException("Failed to parse BlockPos components", e);
        }
    }
}