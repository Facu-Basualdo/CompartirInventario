package com.inventariocompartido;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.inventariocompartido.mixin.EntityEquipmentAccessor;
import com.inventariocompartido.mixin.FoodDataAccessor;
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
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Lo que comparten todos los jugadores del servidor: inventario, armadura, hambre y vida.
 *
 * <p>Inventario, armadura y hambre se comparten por referencia: cada jugador apunta a estos mismos
 * objetos (ver {@link com.inventariocompartido.mixin.InventoryMixin} y
 * {@link com.inventariocompartido.mixin.PlayerMixin}). La vida no se puede compartir así porque vive
 * en los datos sincronizados de cada entidad; la reparte {@link SharedVitals} al final de cada tick.
 *
 * <p>Se guarda en {@code data/inventariocompartido/shared_inventory.dat} dentro del mundo.
 */
public class SharedInventoryState extends SavedData {
	/** Vida desconocida: nadie la fijó todavía, o el grupo acaba de morir. */
	public static final float UNKNOWN_HEALTH = -1;

	private static final Codec<FoodData> FOOD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 20).forGetter(FoodData::getFoodLevel),
			Codec.FLOAT.optionalFieldOf("saturation", 5.0F).forGetter(FoodData::getSaturationLevel),
			Codec.FLOAT.optionalFieldOf("exhaustion", 0.0F).forGetter(food -> ((FoodDataAccessor) food).inventariocompartido$getExhaustionLevel())
	).apply(instance, (level, saturation, exhaustion) -> {
		FoodData food = new FoodData();
		setFood(food, level, saturation, exhaustion);
		return food;
	}));

	private static final Codec<SharedInventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ItemStackWithSlot.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(SharedInventoryState::itemsWithSlots),
			EntityEquipment.CODEC.optionalFieldOf("equipment").forGetter(state -> Optional.of(state.equipment)),
			FOOD_CODEC.optionalFieldOf("food").forGetter(state -> Optional.of(state.foodData)),
			Codec.FLOAT.optionalFieldOf("health", UNKNOWN_HEALTH).forGetter(state -> state.health),
			Codec.BOOL.optionalFieldOf("initialized", false).forGetter(state -> state.initialized)
	).apply(instance, (items, equipment, food, health, initialized) -> new SharedInventoryState(
			items, equipment.orElseGet(EntityEquipment::new), food.orElseGet(FoodData::new), health, initialized)));

	// DataFixTypes null: Fabric API se encarga de que no se intente aplicar data fixers a datos de mods.
	private static final SavedDataType<SharedInventoryState> TYPE = new SavedDataType<>(
			InventarioCompartido.id("shared_inventory"), SharedInventoryState::new, CODEC, null);

	// Estado del servidor que está corriendo ahora. Lo usan los mixins que no tienen acceso al servidor.
	private static @Nullable SharedInventoryState active;

	private final NonNullList<ItemStack> items = NonNullList.withSize(Inventory.INVENTORY_SIZE, ItemStack.EMPTY);
	private final EntityEquipment equipment;
	private final FoodData foodData;
	private float health;
	private boolean initialized;

	// Vida de cada jugador justo después del último reparto. No se guarda: es por objeto ServerPlayer,
	// y un jugador recién creado (login, respawn) no está acá hasta que adopta la vida compartida.
	private final Map<ServerPlayer, Float> syncedHealth = new IdentityHashMap<>();

	// Ítems de antes de instalar el mod, rescatados al cargar el archivo del jugador y pendientes de
	// ponerse en un cofre cuando termine de entrar (ver PersonalItemsChest).
	private final Map<UUID, List<ItemStack>> rescuedItems = new HashMap<>();

	private SharedInventoryState() {
		this(List.of(), new EntityEquipment(), new FoodData(), UNKNOWN_HEALTH, false);
	}

	private SharedInventoryState(List<ItemStackWithSlot> items, EntityEquipment equipment, FoodData foodData, float health, boolean initialized) {
		for (ItemStackWithSlot item : items) {
			if (item.isValidInContainer(this.items.size())) {
				this.items.set(item.slot(), item.stack());
			}
		}
		this.equipment = equipment;
		this.foodData = foodData;
		this.health = health;
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

	public static @Nullable SharedInventoryState active() {
		return active;
	}

	public static void clearActive() {
		active = null;
	}

	/**
	 * Vanilla, al cargar los datos guardados de un jugador, vacía su inventario (y pisa su hambre) con
	 * lo que tenía guardado. Con todo compartido eso pisaría lo del otro jugador, así que se bloquea
	 * una vez que el estado compartido ya está en uso.
	 */
	public static boolean shouldBlockLoadInto(Object storage) {
		SharedInventoryState state = active;
		return state != null && state.initialized
				&& (storage == state.items || storage == state.equipmentItems() || storage == state.foodData);
	}

	public NonNullList<ItemStack> items() {
		return this.items;
	}

	public EnumMap<EquipmentSlot, ItemStack> equipmentItems() {
		return ((EntityEquipmentAccessor) this.equipment).inventariocompartido$getItems();
	}

	public FoodData foodData() {
		return this.foodData;
	}

	/** El hambre vuelve a como la tiene un jugador recién creado. */
	public void resetFood() {
		setFood(this.foodData, 20, 5.0F, 0.0F);
	}

	public float health() {
		return this.health;
	}

	public void setHealth(float health) {
		this.health = health;
	}

	Map<ServerPlayer, Float> syncedHealth() {
		return this.syncedHealth;
	}

	public boolean isInitialized() {
		return this.initialized;
	}

	public void markInitialized() {
		this.initialized = true;
	}

	public void addRescuedItems(UUID player, List<ItemStack> items) {
		this.rescuedItems.computeIfAbsent(player, uuid -> new ArrayList<>()).addAll(items);
	}

	public List<ItemStack> takeRescuedItems(UUID player) {
		List<ItemStack> items = this.rescuedItems.remove(player);
		return items == null ? List.of() : items;
	}

	// Los ItemStack y el hambre se modifican directamente (al romper bloques, comer, correr) sin avisar
	// a nadie, así que se guarda siempre que el mundo se guarde.
	@Override
	public boolean isDirty() {
		return true;
	}

	private static void setFood(FoodData food, int level, float saturation, float exhaustion) {
		food.setFoodLevel(level);
		food.setSaturation(saturation);
		((FoodDataAccessor) food).inventariocompartido$setExhaustionLevel(exhaustion);
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
