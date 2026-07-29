package com.tanman.chattranslator.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.tanman.chattranslator.client.guide.GuideScreen;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!YaclSupport.available()) {
            // YACL is optional. Without it the button opens the built-in guide, which
            // explains the /translate commands that cover every setting.
            return GuideScreen::new;
        }
        return YaclScreens.FACTORY;
    }

    /**
     * Indirection so {@link ChatTranslatorConfigScreen} — and through it every
     * {@code dev.isxander.yacl3} class it references — is only ever loaded when YACL
     * is actually present. Naming the class in a lambda body inside the enclosing
     * class would still put it in that class's constant pool.
     */
    private static final class YaclScreens {
        private static final ConfigScreenFactory<Screen> FACTORY =
                ChatTranslatorConfigScreen::create;

        private YaclScreens() {
        }
    }
}
