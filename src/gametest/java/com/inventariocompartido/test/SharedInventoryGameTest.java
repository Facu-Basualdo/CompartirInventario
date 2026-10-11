package com.inventariocompartido.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import org.jspecify.annotations.Nullable;

import com.inventariocompartido.SharedInventoryState;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class SharedInventoryGameTest {
	// Un solo test: lo compartido es global del servidor, así que tests en paralelo se pisarían.
	// La vida se reparte al final de cada tick, por eso los pasos van en una secuencia con thenIdle(1).
	@GameTest(maxTicks = 100)
	public void playersShareInventoryHungerAndHealth(GameTestHelper helper) {
		PlayerList playerList = helper.getLevel().getServer().getPlayerList();
		ServerPlayer a = joinSurvivalPlayer(helper, "jugador-a");
		ServerPlayer b = joinSurvivalPlayer(helper, "jugador-b");
		ServerPlayer[] respawned = new ServerPlayer[1];
		ServerPlayer[] veteran = new ServerPlayer[1];

		helper.startSequence()
				.thenExecute(() -> {
					// Lo que tira A le llega a B en el momento, no en el próximo tick de B
					a.getInventory().clearContent();
					a.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
					a.getInventory().setSelectedSlot(0);
					a.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket()); // si no, handlePlayerAction ignora el paquete
					RecordingSynchronizer sentToB = new RecordingSynchronizer();
					b.containerMenu.setSynchronizer(sentToB); // marca los 10 como ya enviados
					a.connection.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.DROP_ITEM, BlockPos.ZERO, Direction.DOWN));
					helper.assertTrue(sentToB.sent(Items.COBBLESTONE, 9), Component.literal("B recibe el slot con 9 piedras sin esperar a su tick"));
					helper.getLevel().getEntitiesOfClass(ItemEntity.class, a.getBoundingBox().inflate(5)).forEach(Entity::discard);
					a.getInventory().clearContent();

					// Si A gasta el hacha mientras B pica con ella, a B no le llega hasta que termina: el cliente le reiniciaría el progreso
					BlockPos log = helper.absolutePos(new BlockPos(1, 1, 1));
					helper.getLevel().setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
					b.setPos(log.getX() + 0.5, log.getY(), log.getZ() + 1.5);
					a.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
					b.getInventory().setSelectedSlot(0);
					RecordingSynchronizer sentToMiner = new RecordingSynchronizer();
					b.containerMenu.setSynchronizer(sentToMiner);
					b.gameMode.handleBlockBreakAction(log, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.SOUTH, helper.getLevel().getMaxY(), 0);
					a.getMainHandItem().setDamageValue(1);
					b.containerMenu.broadcastChanges();
					helper.assertFalse(sentToMiner.sentDamaged(Items.IRON_AXE, 1), Component.literal("B no recibe el hacha gastada mientras pica con ella"));
					b.gameMode.handleBlockBreakAction(log, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.SOUTH, helper.getLevel().getMaxY(), 0);
					b.containerMenu.broadcastChanges();
					helper.assertTrue(sentToMiner.sentDamaged(Items.IRON_AXE, 1), Component.literal("B recibe el hacha gastada cuando deja de picar"));
					helper.getLevel().removeBlock(log, false);
					a.getInventory().clearContent();

					// Inventario principal en ambos sentidos
					a.getInventory().add(new ItemStack(Items.DIAMOND, 5));
					assertCount(helper, b, Items.DIAMOND, 5, "B ve los diamantes que agarró A");
					b.getInventory().removeItem(b.getInventory().findSlotMatchingItem(new ItemStack(Items.DIAMOND)), 2);
					assertCount(helper, a, Items.DIAMOND, 3, "A ve que B usó 2 diamantes");

					// Armadura y mano secundaria
					a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
					helper.assertTrue(b.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), Component.literal("B tiene puesto el casco que se puso A"));
					b.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
					helper.assertTrue(a.getOffhandItem().is(Items.SHIELD), Component.literal("A tiene el escudo que B puso en la mano secundaria"));

					// Cargar los datos guardados (viejos) de un jugador no pisa lo compartido
					TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, a.registryAccess());
					a.saveWithoutId(output);
					CompoundTag oldSave = output.buildResult();
					oldSave.remove("UUID"); // si no, B queda con el UUID de A
					a.getInventory().add(new ItemStack(Items.EMERALD, 7));
					a.getFoodData().setFoodLevel(15);
					b.load(TagValueInput.create(ProblemReporter.DISCARDING, b.registryAccess(), oldSave));
					assertCount(helper, a, Items.EMERALD, 7, "Cargar datos viejos de B no borra las esmeraldas nuevas");
					helper.assertTrue(a.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), Component.literal("Cargar datos viejos no borra la armadura compartida"));
					assertFood(helper, b, 15, "Cargar datos viejos no pisa el hambre compartida");

					// Hambre: es el mismo objeto
					helper.assertTrue(a.getFoodData() == b.getFoodData(), Component.literal("A y B usan el mismo FoodData"));
					a.getFoodData().setFoodLevel(7);
					assertFood(helper, b, 7, "B ve el hambre de A");

					// Jugador con partida de antes del mod: sus ítems no entran al inventario compartido…
					TagValueOutput personal = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, a.registryAccess());
					personal.list("Inventory", ItemStackWithSlot.CODEC).add(new ItemStackWithSlot(0, new ItemStack(Items.GOLDEN_APPLE, 3)));
					EntityEquipment personalEquipment = new EntityEquipment();
					personalEquipment.set(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
					personal.store("equipment", EntityEquipment.CODEC, personalEquipment);
					personal.putInt("XpLevel", 3);
					veteran[0] = joinSurvivalPlayer(helper, "jugador-c", personal.buildResult(), helper.absolutePos(new BlockPos(1, 1, 1)));
					assertCount(helper, a, Items.GOLDEN_APPLE, 0, "Las manzanas de antes del mod no entran al inventario compartido");
					helper.assertFalse(a.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS), Component.literal("Las botas de antes del mod no entran a la armadura compartida"));
				})
				.thenIdle(1)
				.thenExecute(() -> {
					// …sino a un cofre al lado suyo
					ServerPlayer c = veteran[0];
					helper.assertTrue(countChests(c) == 1, Component.literal("Aparece un cofre al lado del jugador (hay " + countChests(c) + ")"));
					helper.assertTrue(countInChests(c, Items.GOLDEN_APPLE) == 3, Component.literal("El cofre tiene las 3 manzanas"));
					helper.assertTrue(countInChests(c, Items.DIAMOND_BOOTS) == 1, Component.literal("El cofre tiene las botas"));
					// Su experiencia no se pierde: se suma a la compartida
					assertLevel(helper, a, 3, "A recibe los 3 niveles de antes del mod de C");

					// Un archivo guardado con el mod ya tiene la marca: entrar de nuevo no crea otro cofre
					TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, c.registryAccess());
					c.saveWithoutId(output);
					CompoundTag sharedSave = output.buildResult();
					sharedSave.remove("UUID");
					playerList.remove(c);
					veteran[0] = joinSurvivalPlayer(helper, "jugador-c-otra-vez", sharedSave, helper.absolutePos(new BlockPos(1, 1, 1)));
				})
				.thenIdle(1)
				.thenExecute(() -> {
					helper.assertTrue(countChests(veteran[0]) == 1, Component.literal("Reingresar con un archivo marcado no crea otro cofre (hay " + countChests(veteran[0]) + ")"));
					playerList.remove(veteran[0]);
				})
				.thenIdle(1)
				.thenExecute(() -> a.setHealth(10))
				.thenIdle(1)
				.thenExecute(() -> {
					assertHealth(helper, b, 10, "B recibe el daño de A");
					b.heal(4);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					assertHealth(helper, a, 14, "A recibe la curación de B");
					// Dos golpes en el mismo tick se suman
					a.setHealth(a.getHealth() - 2);
					b.setHealth(b.getHealth() - 3);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					assertHealth(helper, a, 9, "A recibe los dos golpes");
					assertHealth(helper, b, 9, "B recibe los dos golpes");

					// Al recrear al jugador (respawn) sigue compartiendo todo
					respawned[0] = playerList.respawn(a, true, Entity.RemovalReason.CHANGED_DIMENSION);
					respawned[0].connection.player = respawned[0]; // en el juego lo hace handleClientCommand: si no, teleportTo mueve al viejo
					assertCount(helper, respawned[0], Items.EMERALD, 7, "El jugador recreado conserva el inventario compartido");
					assertFood(helper, respawned[0], 7, "El jugador recreado conserva el hambre compartida");
					respawned[0].getInventory().add(new ItemStack(Items.GOLD_INGOT, 4));
					assertCount(helper, b, Items.GOLD_INGOT, 4, "B ve lo que agarra el jugador recreado");
				})
				.thenIdle(1)
				.thenExecute(() -> {
					assertHealth(helper, respawned[0], 9, "El jugador recreado adopta la vida compartida");
					assertHealth(helper, b, 9, "Recrear un jugador no cura a los demás");
					assertLevel(helper, respawned[0], 3, "El jugador recreado adopta la experiencia compartida");

					// Experiencia: se suman los puntos de los dos en el mismo tick (27 + 20 + 5 = 52: nivel 4)
					respawned[0].giveExperiencePoints(20);
					b.giveExperiencePoints(5);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					assertLevel(helper, respawned[0], 4, "A recibe la experiencia de B");
					assertLevel(helper, b, 4, "B recibe la experiencia de A");
					// Gastar niveles (encantar, yunque) también se comparte
					b.giveExperienceLevels(-1);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					assertLevel(helper, respawned[0], 3, "A ve los niveles que gastó B");

					// /intercambiar sin argumento: con un solo compañero, cambian de lugar, de dimensión y de mirada
					ServerLevel overworld = helper.getLevel();
					ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
					Vec3 inOverworld = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
					Vec3 inNether = new Vec3(0.5, 100, 0.5);
					respawned[0].teleportTo(overworld, inOverworld.x, inOverworld.y, inOverworld.z, Set.of(), 0, 0, true);
					b.teleportTo(nether, inNether.x, inNether.y, inNether.z, Set.of(), 90, 0, true);
					runCommand(respawned[0], "intercambiar");
					assertAt(helper, respawned[0], nether, inNether, "A queda donde estaba B, en el Nether");
					assertAt(helper, b, overworld, inOverworld, "B queda donde estaba A");
					helper.assertTrue(respawned[0].getYRot() == 90, Component.literal("A mira para donde miraba B (yRot " + respawned[0].getYRot() + ")"));

					// …y nombrando al compañero
					runCommand(respawned[0], "intercambiar jugador-b");
					assertAt(helper, respawned[0], overworld, inOverworld, "A vuelve a su lugar");
					assertAt(helper, b, nether, inNether, "B vuelve a su lugar");

					// Si uno muere, mueren todos, y el inventario y la experiencia caen una sola vez, donde está el
					// que recibió el golpe. Los dos reciben daño en el mismo tick y ninguno llega a 0 solo: la vida
					// compartida sí. Los jugadores de prueba son invulnerables (no tienen cliente real), así que el
					// daño se simula con setHealth.
					BlockPos here = helper.absolutePos(new BlockPos(1, 1, 1));
					BlockPos away = helper.absolutePos(new BlockPos(1, 1, 12));
					respawned[0].teleportTo(here.getX() + 0.5, here.getY(), here.getZ() + 0.5);
					b.teleportTo(overworld, away.getX() + 0.5, away.getY(), away.getZ() + 0.5, Set.of(), 0, 0, true); // vuelve del Nether
					respawned[0].setHealth(4);
					b.setHealth(5);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					helper.assertTrue(b.isDeadOrDying(), Component.literal("B muere cuando muere A"));
					helper.assertTrue(respawned[0].isDeadOrDying(), Component.literal("A muere por los golpes de los dos"));
					helper.assertTrue(b.getInventory().isEmpty(), Component.literal("Tras la muerte del grupo, el inventario compartido queda vacío"));
					helper.assertTrue(countDropped(respawned[0], Items.EMERALD) == 7, Component.literal("Las esmeraldas caen una vez al lado de A, que recibió más daño"));
					helper.assertTrue(countDropped(b, Items.EMERALD) == 0, Component.literal("Al lado de B no cae nada"));
					helper.assertTrue(droppedExperience(respawned[0]) == 21, Component.literal("A tira la experiencia del grupo una vez (tiró " + droppedExperience(respawned[0]) + ")"));
					helper.assertTrue(droppedExperience(b) == 0, Component.literal("B no tira otra copia de la experiencia"));
					helper.assertTrue(SharedInventoryState.get(helper.getLevel().getServer()).experiencePoints() == 0, Component.literal("La experiencia compartida se pierde al morir"));
					assertFood(helper, b, 20, "El hambre compartida se reinicia al morir");
					helper.getLevel().getEntitiesOfClass(Entity.class, b.getBoundingBox().inflate(20), e -> e instanceof ItemEntity || e instanceof ExperienceOrb).forEach(Entity::discard);
					playerList.remove(respawned[0]);
					playerList.remove(b);
				})
				.thenSucceed();
	}

	/**
	 * Lo mismo que GameTestHelper.makeMockServerPlayerInLevel(), pero sin su subclase anónima: ésa
	 * sobreescribe gameMode() para devolver siempre CREATIVE, y en creativo no se comparten vida ni hambre.
	 */
	private static ServerPlayer joinSurvivalPlayer(GameTestHelper helper, String name) {
		return joinSurvivalPlayer(helper, name, null, null);
	}

	/** Igual, pero cargándole antes un archivo guardado (como hace vanilla en el login) y ubicándolo en pos. */
	private static ServerPlayer joinSurvivalPlayer(GameTestHelper helper, String name, @Nullable CompoundTag savedData, @Nullable BlockPos pos) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		if (savedData != null) {
			player.load(TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), savedData));
		}
		if (pos != null) {
			player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		}
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static List<ChestBlockEntity> chestsAround(ServerPlayer player) {
		List<ChestBlockEntity> chests = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-3, -1, -3), player.blockPosition().offset(3, 2, 3))) {
			if (player.level().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				chests.add(chest);
			}
		}
		return chests;
	}

	private static int countChests(ServerPlayer player) {
		return chestsAround(player).size();
	}

	private static int countInChests(ServerPlayer player, Item item) {
		int count = 0;
		for (ChestBlockEntity chest : chestsAround(player)) {
			count += chest.countItem(item);
		}
		return count;
	}

	/** Registra los slots que el servidor le mandaría al cliente, que en los jugadores de prueba no existe. */
	private static final class RecordingSynchronizer implements ContainerSynchronizer {
		private final List<ItemStack> sentSlots = new ArrayList<>();

		boolean sent(Item item, int count) {
			return this.sentSlots.stream().anyMatch(stack -> stack.is(item) && stack.getCount() == count);
		}

		boolean sentDamaged(Item item, int damage) {
			return this.sentSlots.stream().anyMatch(stack -> stack.is(item) && stack.getDamageValue() == damage);
		}

		@Override
		public void sendInitialData(AbstractContainerMenu menu, List<ItemStack> slots, ItemStack carried, int[] dataSlots) {
		}

		@Override
		public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
			this.sentSlots.add(stack.copy());
		}

		@Override
		public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {
		}

		@Override
		public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
		}

		@Override
		public RemoteSlot createSlot() {
			return new RemoteSlot() {
				private ItemStack last = ItemStack.EMPTY;

				@Override
				public void force(ItemStack stack) {
					this.last = stack.copy();
				}

				@Override
				public void receive(HashedStack stack) {
				}

				@Override
				public boolean matches(ItemStack stack) {
					return ItemStack.matches(this.last, stack);
				}
			};
		}
	}

	private static int countDropped(ServerPlayer player, Item item) {
		return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4), e -> e.getItem().is(item))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static int droppedExperience(ServerPlayer player) {
		return player.level().getEntitiesOfClass(ExperienceOrb.class, player.getBoundingBox().inflate(4))
				.stream().mapToInt(ExperienceOrb::getValue).sum();
	}

	private static void runCommand(ServerPlayer player, String command) {
		player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	private static void assertAt(GameTestHelper helper, ServerPlayer player, ServerLevel level, Vec3 expected, String message) {
		boolean there = player.level() == level && player.position().distanceTo(expected) < 0.01;
		helper.assertTrue(there, Component.literal(message + " (está en " + player.level().dimension().identifier() + " " + player.position() + ")"));
	}

	private static void assertLevel(GameTestHelper helper, Player player, int expected, String message) {
		helper.assertTrue(player.experienceLevel == expected, Component.literal(message + " (nivel " + player.experienceLevel + ", esperado " + expected + ")"));
	}

	private static void assertCount(GameTestHelper helper, Player player, Item item, int expected, String message) {
		int actual = player.getInventory().countItem(item);
		helper.assertTrue(actual == expected, Component.literal(message + " (esperado " + expected + ", hay " + actual + ")"));
	}

	private static void assertHealth(GameTestHelper helper, Player player, float expected, String message) {
		float actual = player.getHealth();
		helper.assertTrue(actual == expected, Component.literal(message + " (esperado " + expected + ", hay " + actual + ")"));
	}

	private static void assertFood(GameTestHelper helper, Player player, int expected, String message) {
		int actual = player.getFoodData().getFoodLevel();
		helper.assertTrue(actual == expected, Component.literal(message + " (esperado " + expected + ", hay " + actual + ")"));
	}
}
