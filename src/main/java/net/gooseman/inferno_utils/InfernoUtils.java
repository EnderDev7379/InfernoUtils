package net.gooseman.inferno_utils;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.gooseman.inferno_utils.config.InfernoConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class InfernoUtils implements ModInitializer {
	public static final String MOD_ID = "inferno_utils";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.



		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient && player.getGameMode() != GameMode.SPECTATOR && player.getStackInHand(hand).isOf(Items.FIREWORK_ROCKET)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
        });

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof PlayerEntity playerEntity) {
				String deathTypeId = damageSource.getTypeRegistryEntry().getIdAsString();
				Entity attacker = damageSource.getAttacker();
				List<String> banExclusions = List.of(InfernoConfig.getStringArray("ban_exclusions"));
				if ((attacker instanceof PlayerEntity || !banExclusions.contains(deathTypeId)) && attacker != entity) {
					MinecraftServer server = playerEntity.getServer();
					String command = null;
                    try {
						command = "tempban " + playerEntity.getDisplayName().getString() + " " + InfernoConfig.config.getOrDefault("ban_time", "10s") + " You have died! If this death was not caused by a player (either directly or via trap), or you think it was otherwise unfair, please contact the moderators through the #tickets discord channel.";
                        server.getCommandSource().getDispatcher().execute(command, server.getCommandSource());
                    } catch (Exception e) {
						playerEntity.sendMessage(Text.of("An error occured, please contact the server owner"), false);
						LOGGER.error("Couldn't tempban player on death!");
                        LOGGER.error("Exception Message: {}", e.getMessage());
						if (command != null) LOGGER.error("Executed Command: {}", command);
                    }
                }
			}
		});

	}
}