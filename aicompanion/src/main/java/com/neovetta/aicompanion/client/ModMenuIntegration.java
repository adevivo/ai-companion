package com.neovetta.aicompanion.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu hook: puts a Configure (gear) button on our entry in the Mods screen, opening the same
 * Cloth Config screen as {@code /companion config}. Mod Menu is optional ({@code suggests} in
 * fabric.mod.json, compile-only in gradle) — this class is loaded only BY Mod Menu via the
 * {@code modmenu} entrypoint, so its absence at runtime is harmless.
 *
 * <p>Cloth Config is optional too, and the two are independent: someone can have Mod Menu without
 * Cloth. In that case there is no screen to open, so no gear button is offered rather than one that
 * throws {@link NoClassDefFoundError} when clicked. {@code /aicompanion config} still works.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!CompanionConfigChat.clothPresent()) {
            // Mod Menu reads a null screen from the factory as "this mod has no config screen",
            // which is exactly right here.
            return parent -> null;
        }
        return CompanionConfigScreen::create;
    }
}
