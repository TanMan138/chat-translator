package com.tanman.chattranslator.client.config;

import net.fabricmc.loader.api.FabricLoader;

/**
 * YACL is a {@code recommends}, not a {@code depends} — the mod must load and work
 * without it, so every entry point into the config screen checks here first.
 */
public final class YaclSupport {

    public static final String MOD_ID = "yet_another_config_lib_v3";

    private YaclSupport() {
    }

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }
}
