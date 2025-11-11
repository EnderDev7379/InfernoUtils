package net.gooseman.inferno_utils.mixin;

import net.gooseman.inferno_utils.config.InfernoConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FoodData.class)
public class FoodDataMixin {
	@ModifyVariable(method = "tick", at = @At(value = "STORE"))
	private boolean modifyShouldRegen(boolean shouldRegen, ServerPlayer player) {
		return shouldRegen && !player.getDisplayName().getString().equals(InfernoConfig.config.getOrDefault("mephisto", null));
	}
}