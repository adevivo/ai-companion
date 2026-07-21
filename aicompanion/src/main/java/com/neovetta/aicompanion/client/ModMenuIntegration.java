package com.neovetta.aicompanion.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu hook: puts a Configure (gear) button on our entry in the Mods screen, opening the same
 * Cloth Config screen as {@code /companion config}. Mod Menu is optional ({@code suggests} in
 * fabric.mod.json, compile-only in gradle) — this class is loaded only BY Mod Menu via the
 * {@code modmenu} entrypoint, so its absence at runtime is harmless.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CompanionConfigScreen::create;
    }
}
