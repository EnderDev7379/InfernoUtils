package net.gooseman.inferno_utils;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.gooseman.inferno_utils.config.InfernoConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

public class InfernoUtils implements ModInitializer {
	public static final String MOD_ID = "inferno_utils";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static HashMap<String, Long> playerCombatTracker = new HashMap<>();

	public List<String> probableTraps = List.of(new String[]{"bad_respawn_point", "falling_anvil", "falling_stalactite", "fireworks", "stalagmite"});
	public List<EntityType<?>> explosionExclusion = List.of(new EntityType<?>[]{EntityType.CREEPER, EntityType.GHAST, EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.END_CRYSTAL});

	public void temporaryBan(PlayerEntity playerEntity, String timeKey, String reasonKey) {
		MinecraftServer server = playerEntity.getServer();
		String command = null;
		try {
			command = String.format("tempban %s %s %s", playerEntity.getDisplayName().getString(), InfernoConfig.config.getOrDefault(timeKey, "8h"), InfernoConfig.config.getOrDefault(reasonKey, "You have died/combat logged"));
			server.getCommandSource().getDispatcher().execute(command, server.getCommandSource());
		} catch (Exception e) {
			playerEntity.sendMessage(Text.of("An error occured, please contact the server owner"), false);
			LOGGER.error("Couldn't tempban player!");
			LOGGER.error("Exception Message: {}", e.getMessage());
			if (command != null) LOGGER.error("Executed Command: {}", command);
		}
	}

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		probableTraps = probableTraps.stream().map((str) -> "minecraft:"+str).toList();

		InfernoConfig.reloadConfig();

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClient && player.getGameMode() != GameMode.SPECTATOR && player.getStackInHand(hand).isOf(Items.FIREWORK_ROCKET)) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
        });

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (entity instanceof PlayerEntity victim && source.getAttacker() instanceof PlayerEntity attacker && victim != attacker) {
				long currentTime = entity.getWorld().getTime();
				playerCombatTracker.put(victim.getUuidAsString(), currentTime);
				playerCombatTracker.put(attacker.getUuidAsString(), currentTime);
			}
		});

		ServerPlayerEvents.LEAVE.register((player) -> {
			InfernoConfig.reloadConfig();
			String playerUuid = player.getUuidAsString();
			long currentTime = player.getWorld().getTime();
			Long lastCombatTime = playerCombatTracker.get(playerUuid);
			if (lastCombatTime != null && currentTime - lastCombatTime <= InfernoConfig.config.getOrDefault("combat_length", 400)) {
				temporaryBan(player, "combat_ban_time", "combat_ban_reason");
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register(((entity, damageSource) -> {
			InfernoConfig.reloadConfig();
			if (!(entity instanceof PlayerEntity player)) return;

			String playerUuid = player.getUuidAsString();
			long currentTime = player.getWorld().getTime();
			Long lastCombatTime = playerCombatTracker.get(playerUuid);

			Entity attacker = damageSource.getAttacker();
			Entity source = damageSource.getSource();
			String deathTypeId = damageSource.getTypeRegistryEntry().getIdAsString();

			if (InfernoConfig.config.getOrDefault("debug", true)) {
                LOGGER.warn("{} died by {}", player.getDisplayName().getString(), deathTypeId);
                LOGGER.warn("Attacker is {}", (attacker != null ? attacker.getDisplayName().getString() : "non existent"));
				LOGGER.warn("Source is {}", (source != null ? source.getDisplayName().getString() : "non existent"));
			}

			if (lastCombatTime != null && currentTime - lastCombatTime <= InfernoConfig.config.getOrDefault("combat_length", 400)) {
				temporaryBan(player, "death_ban_time", "death_ban_reason");
			} else if (probableTraps.contains(deathTypeId) ||
					(deathTypeId.equals("minecraft:indirect_magic") && source.getType() == EntityType.SPLASH_POTION && attacker == null) ||
					((deathTypeId.equals("minecraft:explosion") || deathTypeId.equals("minecraft:player_explosion")) && (attacker == null || (!explosionExclusion.contains(attacker.getType()) && !explosionExclusion.contains(source.getType()))))) {
				temporaryBan(player, "death_ban_time", "death_ban_reason");
			}
		}));

		ServerLifecycleEvents.SERVER_STOPPING.register((server) -> playerCombatTracker.clear());
	}
}