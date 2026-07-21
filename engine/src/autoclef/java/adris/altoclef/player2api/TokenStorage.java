package adris.altoclef.player2api;

import baritone.utils.DirUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TokenStorage {
    private static final Path PATH = DirUtil.getGameDir().resolve("playerengine_token_storage.dat");
    private static final TokenStorage INSTANCE = new TokenStorage();
    private CompoundTag tokensStored = new CompoundTag();

    private TokenStorage(){
        load();
    }

    static String getToken(String username) {
        return getInstance().tokensStored.getString(username);
    }

    static void storeToken(String username, String token) {
        System.out.println("[PlayerEngineTokenStorage]: stored the token for player " + username);
        getInstance().tokensStored.putString(username, token);
        getInstance().save();
    }

    private void load() {
        if (Files.exists(PATH)) {
            try {
                tokensStored = NbtIo.readCompressed(PATH.toFile());
            } catch (IOException var1) {
                var1.printStackTrace();
            }
        }
    }

    private void save() {
        System.out.println("[PlayerEngineTokenStorage]: save() called");
        try {
            NbtIo.writeCompressed(tokensStored, PATH.toFile());
            System.out.println("[PlayerEngineTokenStorage]: Writing to file...");
        } catch (IOException var1) {
            System.err.println("[PlayerEngineTokenStorage]: Writing to file FAILED");
            var1.printStackTrace();
        }
    }

    private static TokenStorage getInstance() {
        return INSTANCE;
    }
}
