package com.soullink.api;

import java.util.function.Consumer;

import com.soullink.LinkGroups;
import com.soullink.SharedExperience;
import com.soullink.SharedInventoryState;
import com.soullink.SharedVitals;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lo que otros mods pueden usar de Soul Link. Hero's Forge lo llama por reflexión, sin depender del jar: no
 * cambies nombres ni firmas sin avisar allá. Todo lo demás del mod es interno y puede cambiar.
 */
public final class SoulLinkApi {
	private SoulLinkApi() {
	}

	/** El id del grupo vinculado del jugador ({@code "general"} si no tiene uno asignado). */
	public static String groupOf(ServerPlayer player) {
		return SharedInventoryState.get(player.level().getServer()).groupIdOf(player.getGameProfile().id());
	}

	/** Reparte ya la vida de cada grupo, en vez de esperar al fin del tick. */
	public static void syncHealth(MinecraftServer server) {
		SharedVitals.syncHealth(server);
	}

	/** Reparte ya la experiencia de cada grupo, en vez de esperar al fin del tick. */
	public static void syncExperience(MinecraftServer server) {
		SharedExperience.sync(server);
	}

	/**
	 * Un cambio de vida de este jugador que no hay que repartir (por ejemplo, el recorte al bajar su vida
	 * máxima): se corre la vida de su último reparto para que no aparezca como daño o curación del grupo.
	 */
	public static void ignoreHealthChange(ServerPlayer player, float delta) {
		SharedInventoryState.get(player.level().getServer()).groupOf(player).syncedHealth().computeIfPresent(player, (who, last) -> last + delta);
	}

	/** Se avisa cada vez que un jugador conectado cambia de grupo, ya con las cosas del grupo nuevo. */
	public static void onGroupChange(Consumer<ServerPlayer> listener) {
		LinkGroups.onChange(listener);
	}
}
