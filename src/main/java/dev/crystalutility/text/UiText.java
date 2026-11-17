package dev.crystalutility.text;

import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;

public final class UiText {
    private static final String PREFIX = "text.crystalutility.";

    private UiText() {
    }

    public static String tr(String english) {
        String key = PREFIX + english;
        return Language.getInstance().has(key) ? Component.translatable(key).getString() : english;
    }

    public static String tr(String english, Object... args) {
        String key = PREFIX + english;
        return Language.getInstance().has(key)
                ? Component.translatable(key, args).getString()
                : String.format(java.util.Locale.ROOT, english, args);
    }
}
