package net.gooseman.inferno_utils;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.gooseman.inferno_utils.command.InfernoUtilsCommand;
import net.gooseman.inferno_utils.config.InfernoConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class InfernoUtils implements ModInitializer {
	public static final String MOD_ID = "inferno_utils";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static HashMap<String, Long> playerCombatTracker = new HashMap<>();

	public static List<String> probableTraps = List.of(new String[]{"bad_respawn_point", "falling_anvil", "falling_stalactite", "fireworks", "stalagmite"});
	public static List<EntityType<?>> explosionExclusion = List.of(new EntityType<?>[]{EntityType.CREEPER, EntityType.GHAST, EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.END_CRYSTAL});

	public static HashMap<UUID, ServerBossEvent> combatBossEvents = new HashMap<>();
	public static HashMap<UUID, UUID> playerCombatBossEvents = new HashMap<>();

	public void temporaryBan(Player playerEntity, String timeKey, String reasonKey) {
		InfernoConfig.reloadConfig();
		MinecraftServer server = playerEntity.getServer();
		String command = null;
		try {
			command = String.format("tempban %s %s %s", playerEntity.getDisplayName().getString(), InfernoConfig.config.getOrDefault(timeKey, "8h"), InfernoConfig.config.getOrDefault(reasonKey, "You have died/combat logged"));
			server.createCommandSourceStack().dispatcher().execute(command, server.createCommandSourceStack());
		} catch (Exception e) {
			playerEntity.displayClientMessage(Component.nullToEmpty("An error occured, please contact the server owner"), false);
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

		InfernoUtilsCommand.register();


		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(level instanceof ServerLevel serverWorld) || !(player instanceof ServerPlayer serverPlayer) || player.gameMode() == GameType.SPECTATOR) return InteractionResult.PASS;

			ItemStack heldItem = player.getItemInHand(hand);
			if (heldItem.is(Items.FIREWORK_ROCKET))
				return InteractionResult.FAIL;

			return InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || player.gameMode() == GameType.SPECTATOR) return InteractionResult.PASS;

			BlockPos blockPos = hitResult.getBlockPos();
			BlockState blockState = level.getBlockState(blockPos);
			ItemStack heldItem = player.getItemInHand(hand);
			if (heldItem.is(ItemTags.HOES) && blockState.is(ModBlockTags.FARMABLE)) {
				if (blockState.is(Blocks.NETHER_WART) || blockState.is(Blocks.BEETROOTS)) {
					if (blockState.getValue(BlockStateProperties.AGE_3) != 3) return InteractionResult.PASS;
				} else if (blockState.getValue(BlockStateProperties.AGE_7) != 7) return InteractionResult.PASS;

				heldItem.hurtAndBreak(1, player, hand);

				Block.getDrops(blockState, serverLevel, blockPos, null, player, heldItem).forEach(stack -> Block.popResource(level, blockPos, (stack.is(Items.WHEAT) || stack.is(Items.BEETROOT) ? stack : stack.copyWithCount(stack.getCount() - 1))));
				blockState.spawnAfterBreak(serverLevel, blockPos, heldItem, true);

				serverLevel.setBlockAndUpdate(blockPos, blockState.getBlock().defaultBlockState());
				serverLevel.gameEvent(player, GameEvent.BLOCK_DESTROY, blockPos);
				serverLevel.playSound(null, blockPos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS);
				return InteractionResult.SUCCESS_SERVER;
			}

			if (heldItem.is(ItemTags.SHOVELS) && (blockState.is(Blocks.DIRT_PATH) || blockState.is(Blocks.FARMLAND))) {
				heldItem.hurtAndBreak(1, serverPlayer, hand);
				serverLevel.setBlockAndUpdate(blockPos, Blocks.DIRT.defaultBlockState());
				serverLevel.playSound(null, blockPos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS);
				return InteractionResult.SUCCESS_SERVER;
			}

			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer attacker && victim != attacker && !attacker.getWeaponItem().isEmpty()) {
				long currentTime = entity.level().getGameTime();
				playerCombatTracker.put(victim.getStringUUID(), currentTime);
				playerCombatTracker.put(attacker.getStringUUID(), currentTime);

				List<UUID> forRemoval = new ArrayList<>();
				combatBossEvents.forEach(((uuid, serverBossEvent) -> {
					serverBossEvent.removePlayer(victim);
					serverBossEvent.removePlayer(attacker);
					if (serverBossEvent.getPlayers().isEmpty())
						forRemoval.add(uuid);
				}));
				forRemoval.forEach(uuid -> combatBossEvents.remove(uuid));

				ServerBossEvent combatBossEvent = new ServerBossEvent(Component.literal("In Combat"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
				combatBossEvents.put(combatBossEvent.getId(), combatBossEvent);

				combatBossEvent.addPlayer(victim);
				playerCombatBossEvents.put(victim.getUUID(), combatBossEvent.getId());
				combatBossEvent.addPlayer(attacker);
				playerCombatBossEvents.put(attacker.getUUID(), combatBossEvent.getId());
			}
		});

		ServerPlayerEvents.LEAVE.register((player) -> {
			InfernoConfig.reloadConfig();
			String playerUuid = player.getStringUUID();
			long currentTime = player.level().getGameTime();
			Long lastCombatTime = playerCombatTracker.get(playerUuid);
			if (lastCombatTime != null && currentTime - lastCombatTime <= InfernoConfig.config.getOrDefault("combat_length", 400)) {
				temporaryBan(player, "combat_ban_time", "combat_ban_reason");
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register(((entity, damageSource) -> {
			InfernoConfig.reloadConfig();
			if (!(entity instanceof Player player)) return;

			String playerUuid = player.getStringUUID();
			long currentTime = player.level().getGameTime();
			Long lastCombatTime = playerCombatTracker.get(playerUuid);

			Entity attacker = damageSource.getEntity();
			Entity source = damageSource.getDirectEntity();
			String deathTypeId = damageSource.typeHolder().getRegisteredName();

			if (InfernoConfig.config.getOrDefault("debug", true)) {
                LOGGER.warn("{} died by {}", player.getDisplayName().getString(), deathTypeId);
                LOGGER.warn("Attacker is {}", (attacker != null ? attacker.getDisplayName().getString() : "non existent"));
				LOGGER.warn("Source is {}", (source != null ? source.getDisplayName().getString() : "non existent"));
			}

			if ((lastCombatTime != null && currentTime - lastCombatTime <= InfernoConfig.config.getOrDefault("combat_length", 400)) ||
					(attacker instanceof Player playerAttacker && playerAttacker != player)) {
				playerCombatTracker.remove(playerUuid);
				temporaryBan(player, "death_ban_time", "death_ban_reason");
			} else if (probableTraps.contains(deathTypeId) ||
					(deathTypeId.equals("minecraft:indirect_magic") && source.getType() == EntityType.SPLASH_POTION && attacker == null) ||
					((deathTypeId.equals("minecraft:explosion") || deathTypeId.equals("minecraft:player_explosion")) && (attacker == null || (!explosionExclusion.contains(attacker.getType()) && !explosionExclusion.contains(source.getType()))) && entity != attacker)) {
				temporaryBan(player, "death_ban_time", "death_ban_reason");
			}
		}));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			List<UUID> forRemoval = new ArrayList<>();
			combatBossEvents.forEach(((uuid, serverBossEvent) -> {
				serverBossEvent.setProgress(serverBossEvent.getProgress() - 0.0025f);
				if (serverBossEvent.getProgress() <= 0f) {
				   	serverBossEvent.removeAllPlayers();
					forRemoval.add(uuid);
				}
			}));
			forRemoval.forEach(uuid -> combatBossEvents.remove(uuid));
		});

		ServerLifecycleEvents.SERVER_STOPPING.register((server) -> playerCombatTracker.clear());
	}
}