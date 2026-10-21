package dev.hd.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class Text {
    public static final TextColor HEART = TextColor.color(0xFF4D6A);
    public static final TextColor GOLD = TextColor.color(0xFFC94D);
    public static final TextColor MUTED = TextColor.color(0xB8B8C8);
    public static final TextColor EPIC = TextColor.color(0xC77DFF);
    public static final TextColor LEGEND = TextColor.color(0xFF9F1C);

    private Text() {}

    public static Component of(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    public static Component info(String s) {
        return Component.text("[Dungeons] ", GOLD).append(Component.text(s, MUTED));
    }

    public static Component error(String s) {
        return Component.text(s, NamedTextColor.RED);
    }
}
