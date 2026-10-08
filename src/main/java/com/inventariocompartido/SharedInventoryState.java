package com.inventariocompartido;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * Lo que comparten todos los jugadores del servidor: inventario, armadura, hambre, vida y experiencia.
 *
 * <p>Inventario, armadura y hambre se comparten por referencia: cada jugador apunta a estos mismos
 * objetos (ver {@link com.inventariocompartido.mixin.InventoryMixin} y
 * {@link com.inventariocompartido.mixin.PlayerMixin}). La vida no se puede compartir así porque vive
 * en los datos sincronizados de cada entidad; la reparte {@link SharedVitals} al final de cada tick. La
 * experiencia tampoco (son campos primitivos): la reparte {@link SharedExperience}.
 *
 * <p>Se guarda en {@code data/inventariocompartido/shared_inventory.dat} dentro del mundo.
 */
public class SharedInventoryState extends SavedData {
	/** Vida desconocida: nadie la fijó todavía, o el grupo acaba de morir. */
	public static final float UNKNOWN_HEALTH = -1;

	/** Experiencia desconocida: el mod recién instalado, todavía no entró nadie. */
	public static final double UNKNOWN_EXPERIENCE = -1;

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
			Codec.BOOL.optionalFieldOf("initialized", false).forGetter(state -> state.initialized),
			Codec.DOUBLE.optionalFieldOf("experience", UNKNOWN_EXPERIENCE).forGetter(state -> state.experiencePoints),
			Codec.INT.optionalFieldOf("experienceScore", 0).forGetter(state -> state.experienceScore)
	).apply(instance, (items, equipment, food, health, initialized, experience, experienceScore) -> new SharedInventoryState(
			items, equipment.orElseGet(EntityEquipment::new), food.orElseGet(FoodData::new), health, initialized,
			experience, experienceScore)));

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
	private double experiencePoints;
	private int experienceScore;

	// Vida de cada jugador justo después del último reparto. No se guarda: es por objeto ServerPlayer,
	// y un jugador recién creado (login, respawn) no está acá hasta que adopta la vida compartida.
	private final Map<ServerPlayer, Float> syncedHealth = new IdentityHashMap<>();
	private final Map<ServerPlayer, SharedExperience.Experience> syncedExperience = new IdentityHashMap<>();

	// Jugadores que entraron con un archivo de antes del mod: su experiencia se suma a la compartida en vez
	// de perderse. Como los ítems rescatados, vive en memoria entre la carga del archivo y el primer tick.
	private final Set<UUID> experienceContributions = new HashSet<>();

	// Quién tiró la experiencia del grupo en este tick (ver SharedExperience.shouldDropExperience).
	private int deathDropTick = -1;
	private @Nullable ServerPlayer deathDropper;

	// Ítems de antes de instalar el mod, rescatados al cargar el archivo del jugador y pendientes de
	// ponerse en un cofre cuando termine de entrar (ver PersonalItemsChest).
	private final Map<UUID, List<ItemStack>> rescuedItems = new HashMap<>();

	private SharedInventoryState() {
		this(List.of(), new EntityEquipment(), new FoodData(), UNKNOWN_HEALTH, false, UNKNOWN_EXPERIENCE, 0);
	}

	private SharedInventoryState(List<ItemStackWithSlot> items, EntityEquipment equipment, FoodData foodData, float health, boolean initialized,
			double experiencePoints, int experienceScore) {
		for (ItemStackWithSlot item : items) {
			if (item.isValidInContainer(this.items.size())) {
				this.items.set(item.slot(), item.stack());
			}
		}
		this.equipment = equipment;
		this.foodData = foodData;
		this.health = health;
		this.initialized = initialized;
		this.experiencePoints = experiencePoints;
		this.experienceScore = experienceScore;
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

	/** Puntos de experiencia (no niveles), o UNKNOWN_EXPERIENCE. */
	public double experiencePoints() {
		return this.experiencePoints;
	}

	/** El puntaje que muestra la pantalla de muerte: la experiencia juntada desde la última muerte. */
	public int experienceScore() {
		return this.experienceScore;
	}

	public void setExperience(double points, int score) {
		this.experiencePoints = points;
		this.experienceScore = score;
	}

	Map<ServerPlayer, SharedExperience.Experience> syncedExperience() {
		return this.syncedExperience;
	}

	public void addExperienceContribution(UUID player) {
		this.experienceContributions.add(player);
	}

	boolean takeExperienceContribution(UUID player) {
		return this.experienceContributions.remove(player);
	}

	/** true si este jugador es el primero en morir en este tick, o ya lo era. */
	boolean claimDeathDrop(ServerPlayer player, int tick) {
		if (this.deathDropTick == tick && this.deathDropper != player) {
			return false;
		}
		this.deathDropTick = tick;
		this.deathDropper = player;
		return true;
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
