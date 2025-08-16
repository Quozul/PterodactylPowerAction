package fr.pickaria.pterodactylpoweraction.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.pickaria.messager.Messager;
import fr.pickaria.messager.components.Text;
import fr.pickaria.pterodactylpoweraction.PterodactylPowerAction;
import fr.pickaria.pterodactylpoweraction.ShutdownManager;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationDoctor;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweraction.configuration.ShutdownBehaviour;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.time.Duration;

public class PterodactylPowerActionCommand {

    private static final Duration DEFAULT_DELAY = Duration.ZERO;
    private static final String COMMAND_NAME = "pterodactylpoweraction";
    private final ProxyServer proxy;
    private final Logger logger;
    private final ConfigurationLoader configurationLoader;
    private final ShutdownManager shutdownManager;
    private final Messager messager;

    public PterodactylPowerActionCommand(ProxyServer proxy, Logger logger, ConfigurationLoader configurationLoader, ShutdownManager shutdownManager) {
        this.proxy = proxy;
        this.logger = logger;
        this.configurationLoader = configurationLoader;
        this.shutdownManager = shutdownManager;
        this.messager = new Messager();
    }

    public BrigadierCommand createBrigadierCommand() {
        LiteralCommandNode<CommandSource> rootNode = BrigadierCommand.literalArgumentBuilder(COMMAND_NAME)
                .requires(source -> source.hasPermission(COMMAND_NAME + ".use"))
                .executes(this::executeHelp)
                .then(BrigadierCommand.literalArgumentBuilder("help").executes(this::executeHelp))
                .then(BrigadierCommand.literalArgumentBuilder("reload").executes(this::executeReload))
                .then(BrigadierCommand.literalArgumentBuilder("doctor").executes(this::executeDoctor))
                .then(
                        BrigadierCommand.literalArgumentBuilder("clear")
                                .then(BrigadierCommand.requiredArgumentBuilder("delay", IntegerArgumentType.integer(0)).executes(this::executeClear))
                                .executes(this::executeClear)
                )
                .build();

        return new BrigadierCommand(rootNode);
    }

    public CommandMeta getCommandMeta(CommandManager commandManager, PterodactylPowerAction pluginContainer) {
        return commandManager.metaBuilder(COMMAND_NAME)
                .aliases("ppa")
                .plugin(pluginContainer)
                .build();
    }

    private int executeHelp(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        messager.info(source, "command.usage", new Text(Component.text("/" + COMMAND_NAME + " <reload|doctor|clear>")));
        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();

        if (configurationLoader.reload()) {
            messager.info(source, "command.reload.success");
        } else {
            messager.error(source, "command.reload.error");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int executeDoctor(CommandContext<CommandSource> context) {
        ConfigurationDoctor doctor = new ConfigurationDoctor(proxy, logger);
        CommandSource source = context.getSource();
        messager.info(source, "command.doctor.start");
        doctor.validateConfig(configurationLoader);
        return Command.SINGLE_SUCCESS;
    }

    private int executeClear(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        messager.info(source, "command.clear.start");

        shutdownManager.shutdownAll(ShutdownBehaviour.SHUTDOWN_EMPTY, getDelayFromContext(context));

        return Command.SINGLE_SUCCESS;
    }

    private Duration getDelayFromContext(CommandContext<CommandSource> context) {
        try {
            int delay = context.getArgument("delay", Integer.class);
            return Duration.ofSeconds(delay);
        } catch (IllegalArgumentException e) {
            return DEFAULT_DELAY;
        }
    }
}
