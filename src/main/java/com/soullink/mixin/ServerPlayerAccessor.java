package com.soullink.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.level.ServerPlayer;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {
	@Accessor("lastSentExp")
	void soullink$setLastSentExp(int lastSentExp);
}
