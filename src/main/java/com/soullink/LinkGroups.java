package com.soullink;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.soullink.mixin.EntityEquipmentAccessor;
import com.soullink.mixin.InventoryAccessor;
import com.soullink.mixin.PlayerAccessor;
import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;

/**
 * Cambiar de grupo a un jugador conectado. Al que se va no le queda nada del grupo anterior: el inventario, la
 * armadura, el hambre, la vida y la experiencia son del grupo, así que pasa a tener los del grupo nuevo. Lo
 * que tenía se queda con su grupo viejo. Llevárselo duplicaría los ítems.
 */
public final class LinkGroups {
	/** Para los ids de grupo: van en comandos, en el archivo del mundo y en el chat. */
	private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,16}");

	private static final List<Consumer<ServerPlayer>> LISTENERS = new CopyOnWriteArrayList<>();

	private LinkGroups() {
	}

	/** El id normalizado (en minúsculas), o null si no sirve. */
	public static @Nullable String normalize(String id) {
		String lower = id.toLowerCase(Locale.ROOT);
		return VALID_ID.matcher(lower).matches() ? lower : null;
	}

	/** Se avisa después de cada cambio de grupo, con el jugador ya enganchado al grupo nuevo. */
	public static void onChange(Consumer<ServerPlayer> listener) {
		LISTENERS.add(listener);
	}

	/** @return false si ya estaba en ese grupo */
	public static boolean move(ServerPlayer player, String groupId) {
		SharedInventoryState state = SharedInventoryState.get(player.level().getServer());
		if (state.groupIdOf(player.getGameProfile().id()).equals(groupId)) {
			return false;
		}
		// Si no, el próximo reparto le contaría al grupo viejo la diferencia con la vida del nuevo como daño
		state.groupOf(player).forget(player);
		state.setGroupOf(player.getGameProfile().id(), player.getGameProfile().name(), groupId);
		LinkGroup group = state.groupOf(player);
		group.forget(player);

		// Lo mismo que hacen los mixins en el constructor, pero sobre un jugador que ya existe. No se crea un
		// ServerPlayer nuevo con PlayerList.respawn: restoreFrom copiaría el inventario viejo en las listas nuevas
		InventoryAccessor inventory = (InventoryAccessor) player.getInventory();
		inventory.soullink$setItems(group.items());
		((EntityEquipmentAccessor) inventory.soullink$getEquipment()).soullink$setItems(group.equipmentItems());
		((PlayerAccessor) player).soullink$setFoodData(group.foodData());

		// broadcastChanges sólo manda lo que cambió en un slot y acá cambió la lista entera: se manda todo
		player.inventoryMenu.sendAllDataToRemote();
		if (player.containerMenu != player.inventoryMenu) {
			player.containerMenu.sendAllDataToRemote();
		}
		// La vida y la experiencia las adopta en el próximo tick, como al entrar (ver SharedVitals y SharedExperience)

		LISTENERS.forEach(listener -> listener.accept(player));
		return true;
	}
}
