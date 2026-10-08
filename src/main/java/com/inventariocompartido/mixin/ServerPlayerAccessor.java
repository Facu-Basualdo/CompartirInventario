package com.inventariocompartido.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.level.ServerPlayer;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {
	@Accessor("lastSentExp")
	void inventariocompartido$setLastSentExp(int lastSentExp);
}
