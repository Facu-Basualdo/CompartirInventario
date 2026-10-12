package com.soullink;

import java.util.List;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /intercambiar [jugador]: el que lo usa y el otro jugador intercambian posición, dimensión y hacia dónde
 * miran. Sin argumento elige solo cuando hay un único compañero en su grupo; con más, hay que nombrarlo.
 * Nombrándolo, sirve con cualquiera, aunque sea de otro grupo.
 */
public final class SwapPlaces {
	/** El mismo comando con nombre en español y en inglés. */
	private static final List<String> NAMES = List.of("intercambiar", "swap");

	private SwapPlaces() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String name : NAMES) {
			dispatcher.register(Commands.literal(name)
					.executes(context -> swap(context.getSource().getPlayerOrException(), onlyPartner(context.getSource())))
					.then(Commands.argument("jugador", EntityArgument.player())
							.executes(context -> swap(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "jugador")))));
		}
	}

	private static ServerPlayer onlyPartner(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer self = source.getPlayerOrException();
		SharedInventoryState state = SharedInventoryState.get(source.getServer());
		String group = state.groupIdOf(self.getGameProfile().id());
		List<ServerPlayer> others = source.getServer().getPlayerList().getPlayers().stream()
				.filter(player -> player != self && state.groupIdOf(player.getGameProfile().id()).equals(group))
				.toList();
		if (others.isEmpty()) {
			throw error(self, "soullink.swap.nobody");
		}
		if (others.size() > 1) {
			throw error(self, "soullink.swap.ambiguous");
		}
		return others.getFirst();
	}

	public static int swap(ServerPlayer self, ServerPlayer other) throws CommandSyntaxException {
		if (self == other) {
			throw error(self, "soullink.swap.self");
		}
		// Un jugador en la pantalla de muerte sigue en PlayerList; teletransportarlo no lo revive.
		if (self.isDeadOrDying() || other.isDeadOrDying()) {
			throw error(self, "soullink.swap.dead");
		}

		Place selfPlace = Place.of(self);
		Place otherPlace = Place.of(other);
		otherPlace.moveHere(self);
		selfPlace.moveHere(other);

		self.sendSystemMessage(Lang.tr(self, "soullink.swap.done", other.getDisplayName()));
		other.sendSystemMessage(Lang.tr(other, "soullink.swap.done_other", self.getDisplayName()));
		return 1;
	}

	/** En el idioma de quien lo usó: por eso no son constantes, como suelen ser los errores de comando. */
	private static CommandSyntaxException error(ServerPlayer player, String key) {
		return new SimpleCommandExceptionType(Lang.tr(player, key)).create();
	}

	/** Se toman las dos antes de mover a nadie: si no, el segundo iría a donde ya llegó el primero. */
	private record Place(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
		static Place of(ServerPlayer player) {
			return new Place(player.level(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
		}

		void moveHere(ServerPlayer player) {
			// Igual que /tp: baja del vehículo, despierta y cambia de dimensión si hace falta.
			player.teleportTo(this.level, this.x, this.y, this.z, Set.of(), this.yRot, this.xRot, true);
			// Si no, el que venía cayendo se lastima al aparecer en el piso, donde estaba el otro.
			player.resetFallDistance();
			if (!player.isFallFlying()) {
				player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
				player.setOnGround(true);
			}
		}
	}
}
