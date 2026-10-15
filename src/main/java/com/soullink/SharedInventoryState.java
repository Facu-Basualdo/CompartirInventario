package com.soullink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Los grupos vinculados del mundo y quién está en cada uno. Lo que comparte cada grupo está en {@link LinkGroup}.
 *
 * <p>Un jugador sin grupo asignado está en {@link #GENERAL}: si nadie arma grupos, todos los del servidor
 * comparten todo, como antes de que existieran los grupos.
 *
 * <p>Se guarda en {@code data/soullink/shared_inventory.dat} dentro del mundo. El grupo general va en la raíz
 * del archivo con los mismos campos que hasta la 2.1.0, y los demás en {@code groups}: un mundo viejo se lee sin
 * migrar nada.
 */
public class SharedInventoryState extends SavedData {
	/** El grupo de los que no tienen uno asignado. */
	public static final String GENERAL = "general";

	/** A quién se le asignó un grupo, con su último nombre para listarlo aunque no esté conectado. */
	record Member(String group, String name) {
		static final Codec<Member> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("group").forGetter(Member::group),
				Codec.STRING.fieldOf("name").forGetter(Member::name)
		).apply(instance, Member::new));
	}

	/** Público sólo para el test que lee un archivo de la 2.1.0. */
	public static final Codec<SharedInventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			LinkGroup.MAP_CODEC.forGetter(state -> state.groups.get(GENERAL)),
			Codec.BOOL.optionalFieldOf("initialized", false).forGetter(state -> state.initialized),
			Codec.unboundedMap(Codec.STRING, LinkGroup.CODEC).optionalFieldOf("groups", Map.of()).forGetter(SharedInventoryState::otherGroups),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Member.CODEC).optionalFieldOf("members", Map.of()).forGetter(state -> state.members),
			Codec.BOOL.optionalFieldOf("trinketsInitialized", false).forGetter(state -> state.trinketsInitialized)
	).apply(instance, SharedInventoryState::new));

	// DataFixTypes null: Fabric API se encarga de que no se intente aplicar data fixers a datos de mods.
	private static final SavedDataType<SharedInventoryState> TYPE = new SavedDataType<>(
			SoulLink.id("shared_inventory"), SharedInventoryState::new, CODEC, null);

	// Estado del servidor que está corriendo ahora. Lo usan los mixins que no tienen acceso al servidor.
	private static @Nullable SharedInventoryState active;

	// En orden de creación: el general primero.
	private final Map<String, LinkGroup> groups = new LinkedHashMap<>();
	private final Map<UUID, Member> members = new HashMap<>();
	private boolean initialized;
	// Como initialized, pero para los slots de Trinkets: puede activarse en un mundo que ya usaba el mod (ver SharedTrinkets).
	private boolean trinketsInitialized;

	// Jugadores que entraron con un archivo de antes del mod: su experiencia se suma a la de su grupo en vez
	// de perderse. Como los ítems rescatados, vive en memoria entre la carga del archivo y el primer tick.
	private final Set<UUID> experienceContributions = new HashSet<>();

	// Ítems de antes de instalar el mod, rescatados al cargar el archivo del jugador y pendientes de
	// ponerse en un cofre cuando termine de entrar (ver PersonalItemsChest).
	private final Map<UUID, List<ItemStack>> rescuedItems = new HashMap<>();

	private SharedInventoryState() {
		this(LinkGroup.create(true), false, Map.of(), Map.of(), false);
	}

	private SharedInventoryState(LinkGroup general, boolean initialized, Map<String, LinkGroup> others, Map<UUID, Member> members,
			boolean trinketsInitialized) {
		this.groups.put(GENERAL, general);
		others.forEach((id, group) -> {
			if (!id.equals(GENERAL)) {
				this.groups.put(id, group);
			}
		});
		this.initialized = initialized;
		this.members.putAll(members);
		this.trinketsInitialized = trinketsInitialized;
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

	/** El grupo de este jugador, o null si no corresponde compartir (lado cliente). */
	public static @Nullable LinkGroup groupFor(Player player) {
		SharedInventoryState state = forPlayer(player);
		return state == null ? null : state.groupOf(player);
	}

	public static @Nullable SharedInventoryState active() {
		return active;
	}

	public static void clearActive() {
		active = null;
	}

	/**
	 * Vanilla, al cargar los datos guardados de un jugador, vacía su inventario (y pisa su hambre) con
	 * lo que tenía guardado. Con todo compartido eso pisaría lo del resto del grupo, así que se bloquea
	 * una vez que el estado compartido ya está en uso.
	 */
	public static boolean shouldBlockLoadInto(Object storage) {
		SharedInventoryState state = active;
		return state != null && state.initialized && state.isShared(storage);
	}

	/** Si {@code storage} es la lista, la armadura o el hambre de algún grupo. */
	public boolean isShared(Object storage) {
		for (LinkGroup group : this.groups.values()) {
			if (group.owns(storage)) {
				return true;
			}
		}
		return false;
	}

	public LinkGroup groupOf(Player player) {
		return group(groupIdOf(player.getGameProfile().id()));
	}

	public String groupIdOf(UUID player) {
		Member member = this.members.get(player);
		return member == null ? GENERAL : member.group();
	}

	/** El grupo con ese id. Si no existe se crea vacío, con la experiencia en 0. */
	public LinkGroup group(String id) {
		return this.groups.computeIfAbsent(id, unused -> LinkGroup.create(false));
	}

	public Map<String, LinkGroup> groups() {
		return Collections.unmodifiableMap(this.groups);
	}

	/** Sólo anota a qué grupo pertenece: reengancharlo a las listas nuevas es cosa de {@link LinkGroups#move}. */
	void setGroupOf(UUID player, String name, String group) {
		if (group.equals(GENERAL)) {
			this.members.remove(player);
		} else {
			group(group);
			this.members.put(player, new Member(group, name));
		}
	}

	/** Los nombres de los que tienen un grupo asignado, por grupo y en orden alfabético. */
	public Map<String, List<String>> assignedNames() {
		Map<String, List<String>> result = new TreeMap<>();
		for (Member member : this.members.values()) {
			result.computeIfAbsent(member.group(), unused -> new ArrayList<>()).add(member.name());
		}
		result.values().forEach(names -> names.sort(String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	/** Los conectados, separados por grupo. Sólo aparecen los grupos con alguien conectado. */
	public Map<String, List<ServerPlayer>> onlineByGroup(List<ServerPlayer> players) {
		Map<String, List<ServerPlayer>> result = new LinkedHashMap<>();
		for (ServerPlayer player : players) {
			result.computeIfAbsent(groupIdOf(player.getGameProfile().id()), unused -> new ArrayList<>()).add(player);
		}
		return result;
	}

	public void addExperienceContribution(UUID player) {
		this.experienceContributions.add(player);
	}

	boolean takeExperienceContribution(UUID player) {
		return this.experienceContributions.remove(player);
	}

	public boolean isInitialized() {
		return this.initialized;
	}

	public void markInitialized() {
		this.initialized = true;
	}

	public boolean isTrinketsInitialized() {
		return this.trinketsInitialized;
	}

	public void markTrinketsInitialized() {
		this.trinketsInitialized = true;
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

	private Map<String, LinkGroup> otherGroups() {
		Map<String, LinkGroup> others = new LinkedHashMap<>(this.groups);
		others.remove(GENERAL);
		return others;
	}
}
