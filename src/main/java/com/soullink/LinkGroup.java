package com.soullink;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.soullink.mixin.EntityEquipmentAccessor;
import com.soullink.mixin.FoodDataAccessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

/**
 * Lo que comparten los jugadores de un grupo vinculado: inventario, armadura, hambre, vida y experiencia.
 *
 * <p>Inventario, armadura y hambre se comparten por referencia: cada jugador del grupo apunta a estos mismos
 * objetos (ver {@link com.soullink.mixin.InventoryMixin} y {@link com.soullink.mixin.PlayerMixin}). La vida
 * no se puede compartir así porque vive en los datos sincronizados de cada entidad; la reparte
 * {@link SharedVitals} al final de cada tick. La experiencia tampoco (son campos primitivos): la reparte
 * {@link SharedExperience}.
 */
public final class LinkGroup {
	/** Vida desconocida: nadie la fijó todavía, o el grupo acaba de morir. */
	public static final float UNKNOWN_HEALTH = -1;

	/** Experiencia desconocida: el mod recién instalado, todavía no entró nadie. */
	public static final double UNKNOWN_EXPERIENCE = -1;

	private static final Codec<FoodData> FOOD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 20).forGetter(FoodData::getFoodLevel),
			Codec.FLOAT.optionalFieldOf("saturation", 5.0F).forGetter(FoodData::getSaturationLevel),
			Codec.FLOAT.optionalFieldOf("exhaustion", 0.0F).forGetter(food -> ((FoodDataAccessor) food).soullink$getExhaustionLevel())
	).apply(instance, (level, saturation, exhaustion) -> {
		FoodData food = new FoodData();
		setFood(food, level, saturation, exhaustion);
		return food;
	}));

	/**
	 * Los mismos campos que tenía el archivo de un solo grupo, hasta la 2.1.0: así el grupo de antes se lee
	 * igual desde la raíz del archivo viejo (ver {@link SharedInventoryState}).
	 */
	static final MapCodec<LinkGroup> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ItemStackWithSlot.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(LinkGroup::itemsWithSlots),
			EntityEquipment.CODEC.optionalFieldOf("equipment").forGetter(group -> Optional.of(group.equipment)),
			FOOD_CODEC.optionalFieldOf("food").forGetter(group -> Optional.of(group.foodData)),
			Codec.FLOAT.optionalFieldOf("health", UNKNOWN_HEALTH).forGetter(group -> group.health),
			Codec.DOUBLE.optionalFieldOf("experience", UNKNOWN_EXPERIENCE).forGetter(group -> group.experiencePoints),
			Codec.INT.optionalFieldOf("experienceScore", 0).forGetter(group -> group.experienceScore)
	).apply(instance, (items, equipment, food, health, experience, experienceScore) -> new LinkGroup(
			items, equipment.orElseGet(EntityEquipment::new), food.orElseGet(FoodData::new), health, experience, experienceScore)));

	static final Codec<LinkGroup> CODEC = MAP_CODEC.codec();

	private final NonNullList<ItemStack> items = NonNullList.withSize(Inventory.INVENTORY_SIZE, ItemStack.EMPTY);
	private final EntityEquipment equipment;
	private final FoodData foodData;
	private float health;
	private double experiencePoints;
	private int experienceScore;

	// Vida de cada jugador justo después del último reparto. No se guarda: es por objeto ServerPlayer,
	// y un jugador recién creado (login, respawn, cambio de grupo) no está acá hasta que adopta la vida del grupo.
	private final Map<ServerPlayer, Float> syncedHealth = new IdentityHashMap<>();
	private final Map<ServerPlayer, SharedExperience.Experience> syncedExperience = new IdentityHashMap<>();

	// Quién tiró la experiencia del grupo en este tick (ver SharedExperience.shouldDropExperience).
	private int deathDropTick = -1;
	private @Nullable ServerPlayer deathDropper;

	private LinkGroup(List<ItemStackWithSlot> items, EntityEquipment equipment, FoodData foodData, float health, double experiencePoints,
			int experienceScore) {
		for (ItemStackWithSlot item : items) {
			if (item.isValidInContainer(this.items.size())) {
				this.items.set(item.slot(), item.stack());
			}
		}
		this.equipment = equipment;
		this.foodData = foodData;
		this.health = health;
		this.experiencePoints = experiencePoints;
		this.experienceScore = experienceScore;
	}

	/**
	 * El grupo de un mundo recién instalado: el primero que entra aporta su experiencia. Un grupo armado con
	 * /soullink arranca en 0, porque los que entran ya tienen la experiencia de su grupo anterior: aportarla
	 * la duplicaría.
	 */
	static LinkGroup create(boolean firstGroup) {
		return new LinkGroup(List.of(), new EntityEquipment(), new FoodData(), UNKNOWN_HEALTH, firstGroup ? UNKNOWN_EXPERIENCE : 0, 0);
	}

	public NonNullList<ItemStack> items() {
		return this.items;
	}

	public EnumMap<EquipmentSlot, ItemStack> equipmentItems() {
		return ((EntityEquipmentAccessor) this.equipment).soullink$getItems();
	}

	public FoodData foodData() {
		return this.foodData;
	}

	/** El hambre vuelve a como la tiene un jugador recién creado. */
	public void resetFood() {
		setFood(this.foodData, 20, 5.0F, 0.0F);
	}

	/** Si {@code storage} es una de las listas o el hambre de este grupo. */
	boolean owns(Object storage) {
		return storage == this.items || storage == equipmentItems() || storage == this.foodData;
	}

	public float health() {
		return this.health;
	}

	public void setHealth(float health) {
		this.health = health;
	}

	public Map<ServerPlayer, Float> syncedHealth() {
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

	/** Se va del grupo: el próximo reparto no le cuenta ningún delta, y en el grupo nuevo adopta. */
	void forget(ServerPlayer player) {
		this.syncedHealth.remove(player);
		this.syncedExperience.remove(player);
	}

	/** true si este jugador es el primero del grupo en morir en este tick, o ya lo era. */
	boolean claimDeathDrop(ServerPlayer player, int tick) {
		if (this.deathDropTick == tick && this.deathDropper != player) {
			return false;
		}
		this.deathDropTick = tick;
		this.deathDropper = player;
		return true;
	}

	private static void setFood(FoodData food, int level, float saturation, float exhaustion) {
		food.setFoodLevel(level);
		food.setSaturation(saturation);
		((FoodDataAccessor) food).soullink$setExhaustionLevel(exhaustion);
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
