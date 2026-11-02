package fr.pickaria.pterodactylpoweraction;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.translation.GlobalTranslator;

import java.util.Locale;

public final class MessagesHelper {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Component INFO_PREFIX = MINI_MESSAGE.deserialize("<dark_gray>[<bold><gold>!</bold><dark_gray>]");
    private static final Component ERROR_PREFIX = MINI_MESSAGE.deserialize("<dark_red>[<bold><red>!</bold><dark_red>]");
    private static final TextColor INFO_COLOR = NamedTextColor.GRAY;
    private static final TextColor INFO_ACCENT = NamedTextColor.GOLD;
    private static final TextColor ERROR_COLOR = NamedTextColor.RED;
    private static final TextColor ERROR_ACCENT = NamedTextColor.GRAY;

    private MessagesHelper() {
    }

    public static TextColor infoAccent() {
        return INFO_ACCENT;
    }

    public static void info(Audience audience, String key, ComponentLike... arguments) {
        audience.sendMessage(format(audience, INFO_PREFIX, INFO_COLOR, INFO_ACCENT, key, arguments));
    }

    public static void error(Audience audience, String key, ComponentLike... arguments) {
        audience.sendMessage(format(audience, ERROR_PREFIX, ERROR_COLOR, ERROR_ACCENT, key, arguments));
    }

    public static Component formatInfo(Audience audience, String key, ComponentLike... arguments) {
        return format(audience, INFO_PREFIX, INFO_COLOR, INFO_ACCENT, key, arguments);
    }

    private static Component format(Audience audience, Component prefix, TextColor color, TextColor accent, String key, ComponentLike... arguments) {
        Component[] coloredArguments = new Component[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            coloredArguments[i] = ComponentLike.unbox(arguments[i]).colorIfAbsent(accent);
        }

        Component content = Component.translatable(key, coloredArguments).color(color);
        Component message = prefix.appendSpace().append(content);

        Locale locale = audience.get(Identity.LOCALE).orElse(Locale.ROOT);
        return GlobalTranslator.render(message, locale);
    }
}
