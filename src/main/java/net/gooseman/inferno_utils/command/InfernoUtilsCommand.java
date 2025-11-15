package net.gooseman.inferno_utils.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.gooseman.inferno_utils.InfernoUtils;
import net.gooseman.inferno_utils.config.InfernoConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class InfernoUtilsCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((commandDispatcher, commandBuildContext, commandSelection) -> {
            LiteralCommandNode<CommandSourceStack> rootNode = Commands.literal("infernoutils").build();

            LiteralCommandNode<CommandSourceStack> versionNode = Commands.literal("version").executes(InfernoUtilsCommand::getVersion).build();

            LiteralCommandNode<CommandSourceStack> reloadConfigNode = Commands.literal("reloadConfig").executes(InfernoUtilsCommand::reloadConfig).build();


            commandDispatcher.getRoot().addChild(rootNode);
            rootNode.addChild(versionNode);
            rootNode.addChild(reloadConfigNode);
        });
    }

    public static int getVersion(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(FabricLoader.getInstance().getModContainer(InfernoUtils.MOD_ID).get().getMetadata().getVersion().getFriendlyString()), false);
        return 1;
    }

    public static int reloadConfig(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().hasPermission(4)) {
            context.getSource().sendFailure(Component.literal("You do not have permission to run this command!"));
            return -1;
        }
        InfernoConfig.reloadConfig();
        context.getSource().sendSuccess(() -> Component.literal("Configuration file reloaded!"), true);
        return 1;
    }
}
