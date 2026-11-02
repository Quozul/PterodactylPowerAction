package fr.pickaria.pterodactylpoweraction.component;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class RunCommand {
    private final String command;
    private final Component displayComponent;

    public RunCommand(String command, Component displayComponent) {
        assert command.startsWith("/");

        this.command = command;
        this.displayComponent = displayComponent;
    }

    public Component getComponent(TextColor accentColor) {
        return accented(displayComponent, accentColor)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(this.command))
                .hoverEvent(getHoverComponent(accentColor));
    }

    private Component accented(Component component, TextColor accentColor) {
        return component.color(accentColor);
    }

    private Component getHoverComponent(TextColor accentColor) {
        return Component.translatable("run.command", accented(Component.text(command.trim()), accentColor));
    }
}
