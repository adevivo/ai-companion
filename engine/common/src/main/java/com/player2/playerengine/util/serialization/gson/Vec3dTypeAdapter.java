package com.player2.playerengine.util.serialization.gson;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.world.phys.Vec3;

public class Vec3dTypeAdapter extends AbstractVectorTypeAdapter<Vec3> {

    @Override
    protected Collection<String> getParts(Vec3 value) {
        return Arrays.asList(
                String.valueOf(value.x()),
                String.valueOf(value.y()),
                String.valueOf(value.z())
        );
    }

    @Override
    protected Vec3 fromParts(String[] parts) throws IOException {
        if (parts.length != 3) {
            throw new IOException("Invalid Vec3 string: needs 3 components (x,y,z), but found " + parts.length);
        }
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            return new Vec3(x, y, z);
        } catch (NumberFormatException e) {
            throw new IOException("Failed to parse Vec3 components from string: " + Arrays.toString(parts), e);
        }
    }
}