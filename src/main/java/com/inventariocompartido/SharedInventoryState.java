package com.inventariocompartido;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import com.inventariocompartido.mixin.EntityEquipmentAccessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * El único inventario del servidor. Cada jugador apunta a estas mismas listas en memoria
 * (ver {@link com.inventariocompartido.mixin.InventoryMixin}), así que cualquier cambio que haga
 * uno lo ve el otro en el siguiente tick: el sync de menús de vanilla detecta el slot cambiado
 * y se lo manda a cada cliente.
 *
 * <p>Se guarda en {@code data/inventariocompartido/shared_inventory.dat} dentro del mundo.
 */
public class SharedInventoryState extends SavedData {
	private static final Codec<SharedInventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ItemStackWithSlot.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(SharedInventoryState::itemsWithSlots),
			EntityEquipment.CODEC.optionalFieldOf("equipment").forGetter(state -> Optional.of(state.equipment)),
			Codec.BOOL.optionalFieldOf("initialized", false).forGetter(state -> state.initialized)
	).apply(instance, (items, equipment, initialized) ->
			new SharedInventoryState(items, equipment.orElseGet(EntityEquipment::new), initialized)));

	// DataFixTypes null: Fabric API se encarga de que no se intente aplicar data fixers a datos de mods.
	private static final SavedDataType<SharedInventoryState> TYPE = new SavedDataType<>(
			InventarioCompartido.id("shared_inventory"), SharedInventoryState::new, CODEC, null);

	// Estado del servidor que está corriendo ahora. Lo usan los mixins que no tienen acceso al servidor.
	private static @Nullable SharedInventoryState active;

	private final NonNullList<ItemStack> items = NonNullList.withSize(Inventory.INVENTORY_SIZE, ItemStack.EMPTY);
	private final EntityEquipment equipment;
	private boolean initialized;

	private SharedInventoryState() {
		this(List.of(), new EntityEquipment(), false);
	}

	private SharedInventoryState(List<ItemStackWithSlot> items, EntityEquipment equipment, boolean initialized) {
		for (ItemStackWithSlot item : items) {
			if (item.isValidInContainer(this.items.size())) {
				this.items.set(item.slot(), item.stack());
			}
		}
		this.equipment = equipment;
		this.initialized = initialized;
	}

	public static SharedInventoryState get(MinecraftServer server) {
		SharedInventoryState state = server.getDataStorage().computeIfAbsent(TYPE);
		active = state;
		return state;
	}

	/** Estado compartido para este jugador, o null si no corresponde compartir (lado cliente). */
	public static @Nullable SharedInventoryState forPlayer(Player player) {
		if (!(player instanceof ServerPlayer) || player.level().isClientSide()) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		return server == null ? null : get(server);
	}

	public static void clearActive() {
		active = null;
	}

	/**
	 * Vanilla, al cargar los datos guardados de un jugador, vacía su inventario y lo rellena con lo
	 * que tenía guardado. Con el inventario compartido eso pisaría lo que tiene el otro jugador,
	 * así que se bloquea una vez que el inventario compartido ya está en uso.
	 */
	public static boolean shouldBlockLoadInto(Object storage) {
		SharedInventoryState state = active;
		return state != null && state.initialized && (storage == state.items || storage == state.equipmentItems());
	}

	public NonNullList<ItemStack> items() {
		return this.items;
	}

	public EnumMap<EquipmentSlot, ItemStack> equipmentItems() {
		return ((EntityEquipmentAccessor) this.equipment).inventariocompartido$getItems();
	}

	public void markInitialized() {
		this.initialized = true;
	}

	// Los ItemStack se modifican directamente (al romper bloques, comer, etc.) sin avisar a nadie,
	// así que se guarda siempre que el mundo se guarde.
	@Override
	public boolean isDirty() {
		return true;
	}

	private List<ItemStackWithSlot> itemsWithSlots() {
		List<ItemStackWithSlot> result = new ArrayList<>();
		for (int slot = 0; slot < this.items.size(); slot++) {
			ItemStack stack = this.items.get(slot);
			if (!stack.isEmpty()) {
				result.add(new ItemStackWithSlot(slot, stack));
			}
		}
		return result;
	}
}
