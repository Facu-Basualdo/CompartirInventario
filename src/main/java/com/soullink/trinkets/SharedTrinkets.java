package com.soullink.trinkets;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.soullink.LinkGroup;
import com.soullink.LinkGroups;
import com.soullink.SharedInventoryState;
import com.soullink.TrinketSlotLists;
import com.soullink.mixin.trinkets.TrinketInventoryAccessor;
import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import eu.pb4.trinkets.impl.TrinketInventoryImpl;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Los slots de Trinkets (Trinkets Updated) se comparten como el inventario: cada {@link TrinketInventoryImpl} de un
 * jugador del grupo apunta a las mismas listas de stacks ({@link TrinketSlotLists} del {@link LinkGroup}). Esta clase
 * sólo se carga si Trinkets está instalado (ver TrinketsMixinPlugin y {@link #init()}).
 */
public final class SharedTrinkets {
	/** Marca en el archivo del jugador: los trinkets guardados son una copia de los compartidos. */
	public static final String SHARED_MARKER = "SoulLinkTrinkets";

	private SharedTrinkets() {
	}

	/** Lo llama SoulLink.onInitialize sólo con Trinkets instalado. */
	public static void init() {
		// Después de rescatar los ítems del primero que entra: desde acá los trinkets guardados de cada uno se descartan
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SharedInventoryState.get(server).markTrinketsInitialized());
		LinkGroups.onChange(SharedTrinkets::bindAll);
	}

	/**
	 * Engancha un slot de Trinkets del jugador a las listas de su grupo. Si el grupo todavía no tiene ese slot, las
	 * listas del jugador pasan a ser las compartidas. Corre al crear el inventario, cada vez que Trinkets lo
	 * redimensiona ({@code update}) y al terminar de construir el ServerPlayer.
	 */
	public static void bind(TrinketInventoryImpl inventory) {
		TrinketAttachment attachment = inventory.getAttachment();
		// En el constructor de LivingEntity el jugador todavía no tiene perfil: se engancha al final del de ServerPlayer
		if (attachment == null || !(attachment.getEntity() instanceof ServerPlayer player) || player.getGameProfile() == null) {
			return;
		}
		LinkGroup group = SharedInventoryState.groupFor(player);
		if (group == null) {
			return;
		}
		TrinketInventoryAccessor accessor = (TrinketInventoryAccessor) (Object) inventory;
		String id = inventory.slotType().getId();
		TrinketSlotLists shared = group.trinketSlot(id);
		if (shared == null) {
			SharedInventoryState state = SharedInventoryState.forPlayer(player);
			if (state != null && isSharedByAnyGroup(state, accessor.soullink$getStacks())) {
				// Viene de otro grupo (/soullink grupo): el nuevo arranca vacío, lo de antes se queda con el grupo viejo
				shared = new TrinketSlotLists(NonNullList.withSize(accessor.soullink$getStacks().size(), ItemStack.EMPTY),
						NonNullList.withSize(accessor.soullink$getCosmeticStacks().size(), ItemStack.EMPTY));
				group.putTrinketSlot(id, shared);
			} else {
				group.putTrinketSlot(id, new TrinketSlotLists(accessor.soullink$getStacks(), accessor.soullink$getCosmeticStacks()));
				return;
			}
		}
		if (accessor.soullink$getStacks() == shared.stacks() && accessor.soullink$getCosmeticStacks() == shared.cosmetic()) {
			return;
		}
		int size = accessor.soullink$getStacks().size();
		int cosmeticSize = accessor.soullink$getCosmeticStacks().size();
		if (shared.stacks().size() != size || shared.cosmetic().size() != cosmeticSize) {
			// Cambió la cantidad de lugares del slot (datapack o modificadores): se rearma con el tamaño nuevo y lo que
			// no entra cae a los pies del jugador
			shared = new TrinketSlotLists(resize(shared.stacks(), size, player), resize(shared.cosmetic(), cosmeticSize, player));
			group.putTrinketSlot(id, shared);
		}
		accessor.soullink$setStacks(shared.stacks());
		accessor.soullink$setCosmeticStacks(shared.cosmetic());
	}

	/** Todos los slots de Trinkets del jugador: al terminar de construirlo y al cambiarlo de grupo. */
	public static void bindAll(ServerPlayer player) {
		TrinketAttachment attachment = TrinketsApi.getAttachment(player);
		if (attachment == null) {
			return;
		}
		for (TrinketInventory inventory : attachment.getInventories().values()) {
			if (inventory instanceof TrinketInventoryImpl impl) {
				bind(impl);
			}
		}
	}

	/** Lo que había en las listas compartidas antes de cargar el archivo del jugador (ver TrinketsLoadMixin). */
	public record LoadSnapshot(ServerPlayer player, boolean marked, Map<NonNullList<ItemStack>, List<ItemStack>> lists) {
	}

	/**
	 * Antes de que el jugador cargue su archivo: Trinkets lee sus trinkets guardados (o vacía los slots si no hay)
	 * directo sobre las listas, que son las del grupo. Se guarda qué había para devolverlo después.
	 */
	public static @Nullable LoadSnapshot beforeLoad(Entity entity, ValueInput input) {
		if (!(entity instanceof ServerPlayer player) || SharedInventoryState.groupFor(player) == null) {
			return null;
		}
		bindAll(player);
		TrinketAttachment attachment = TrinketsApi.getAttachment(player);
		if (attachment == null) {
			return null;
		}
		Map<NonNullList<ItemStack>, List<ItemStack>> lists = new IdentityHashMap<>();
		for (TrinketInventory inventory : attachment.getInventories().values()) {
			if (inventory instanceof TrinketInventoryAccessor accessor) {
				lists.put(accessor.soullink$getStacks(), new ArrayList<>(accessor.soullink$getStacks()));
				lists.put(accessor.soullink$getCosmeticStacks(), new ArrayList<>(accessor.soullink$getCosmeticStacks()));
			}
		}
		return new LoadSnapshot(player, input.getBooleanOr(SHARED_MARKER, false), lists);
	}

	/**
	 * Después de cargar: se devuelve lo compartido a como estaba. Si el archivo es de antes de compartir los trinkets
	 * (sin la marca), lo que traía es del jugador y va al cofre de ítems rescatados, como el inventario. Con el estado
	 * todavía sin inicializar (el primero que entra desde que se comparten), lo suyo queda como lo compartido.
	 */
	public static void afterLoad(LoadSnapshot snapshot) {
		SharedInventoryState state = SharedInventoryState.forPlayer(snapshot.player());
		if (state == null || !state.isTrinketsInitialized()) {
			return;
		}
		List<ItemStack> rescued = new ArrayList<>();
		snapshot.lists().forEach((list, before) -> {
			for (int slot = 0; slot < Math.min(list.size(), before.size()); slot++) {
				ItemStack loaded = list.get(slot);
				ItemStack shared = before.get(slot);
				if (loaded == shared) {
					continue;
				}
				// Una copia igual a lo compartido no es del jugador: Trinkets copia los ítems al reconstruir los slots
				if (!snapshot.marked() && !loaded.isEmpty() && !ItemStack.matches(loaded, shared)) {
					rescued.add(loaded);
				}
				list.set(slot, shared);
			}
		});
		if (!rescued.isEmpty()) {
			state.addRescuedItems(snapshot.player().getGameProfile().id(), rescued);
		}
	}

	private static boolean isSharedByAnyGroup(SharedInventoryState state, Object list) {
		for (LinkGroup group : state.groups().values()) {
			if (group.ownsTrinketList(list)) {
				return true;
			}
		}
		return false;
	}

	private static NonNullList<ItemStack> resize(NonNullList<ItemStack> list, int size, ServerPlayer player) {
		NonNullList<ItemStack> resized = NonNullList.withSize(size, ItemStack.EMPTY);
		for (int slot = 0; slot < list.size(); slot++) {
			if (slot < size) {
				resized.set(slot, list.get(slot));
			} else if (!list.get(slot).isEmpty()) {
				Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), list.get(slot));
			}
		}
		return resized;
	}
}
