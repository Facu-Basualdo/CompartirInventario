package com.soullink.mixin;

import com.soullink.BlockMiner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin implements BlockMiner {
	@Shadow
	@Final
	protected ServerPlayer player;

	@Shadow
	private boolean isDestroyingBlock;

	@Unique
	private ItemStack soullink$destroyingItem = ItemStack.EMPTY;

	// Es el mismo ítem que el cliente anota al empezar a picar (MultiPlayerGameMode.destroyingItem).
	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
	private void soullink$rememberDestroyingItem(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence, CallbackInfo ci) {
		if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
			this.soullink$destroyingItem = this.player.getMainHandItem();
		}
	}

	@Override
	public boolean soullink$isMiningWith(ItemStack stack) {
		return this.isDestroyingBlock && stack == this.soullink$destroyingItem && !stack.isEmpty();
	}
}
