// client commands: /jointed debug [on|off], /jointed demo <name> [stop], /jointed stats
package k.jointed.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import k.jointed.client.demo.Demos;
import k.jointed.client.render.DebugState;
import k.jointed.client.render.WorldChains;
import k.jointed.client.rig.RigManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class JointedCommands {
    private JointedCommands() {}

    public static void onRegister(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("jointed");

        root.then(Commands.literal("debug")
                .executes(c -> setDebug(c.getSource(), !DebugState.enabled))
                .then(Commands.literal("on").executes(c -> setDebug(c.getSource(), true)))
                .then(Commands.literal("off").executes(c -> setDebug(c.getSource(), false))));

        root.then(Commands.literal("demo")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Demos.NAMES, b))
                        .executes(c -> demo(c.getSource(), StringArgumentType.getString(c, "name"), false))
                        .then(Commands.literal("stop")
                                .executes(c -> demo(c.getSource(), StringArgumentType.getString(c, "name"), true)))));

        root.then(Commands.literal("stats").executes(c -> {
            c.getSource().sendSuccess(() -> Component.translatable("jointed.command.stats",
                    RigManager.registrations().size(), RigManager.trackedEntities(), WorldChains.count()), false);
            return 1;
        }));

        event.getDispatcher().register(root);
    }

    private static int setDebug(CommandSourceStack src, boolean on) {
        DebugState.enabled = on;
        src.sendSuccess(() -> Component.translatable(on ? "jointed.command.debug.on" : "jointed.command.debug.off"), false);
        return 1;
    }

    private static int demo(CommandSourceStack src, String name, boolean stop) {
        if (!Demos.NAMES.contains(name)) {
            src.sendFailure(Component.translatable("jointed.command.demo.unknown", name));
            return 0;
        }
        if (stop) {
            Demos.stop(name);
            src.sendSuccess(() -> Component.translatable("jointed.command.demo.stopped", name), false);
        } else {
            Demos.start(name);
            src.sendSuccess(() -> Component.translatable("jointed.command.demo.started", name), false);
        }
        return 1;
    }
}
