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
	private static final SimpleCommandExceptionType NOBODY = new SimpleCommandExceptionType(
			Component.literal("No hay nadie de tu grupo conectado: usá /intercambiar <jugador>"));
	private static final SimpleCommandExceptionType AMBIGUOUS = new SimpleCommandExceptionType(
			Component.literal("Hay varios en tu grupo: usá /intercambiar <jugador>"));
	private static final SimpleCommandExceptionType SELF = new SimpleCommandExceptionType(
			Component.literal("No podés intercambiar lugar con vos mismo"));
	private static final SimpleCommandExceptionType DEAD = new SimpleCommandExceptionType(
			Component.literal("No se puede intercambiar lugar mientras alguno de los dos está muerto"));

	private SwapPlaces() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("intercambiar")
				.executes(context -> swap(context.getSource().getPlayerOrException(), onlyPartner(context.getSource())))
				.then(Commands.argument("jugador", EntityArgument.player())
						.executes(context -> swap(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "jugador")))));
	}

	private static ServerPlayer onlyPartner(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer self = source.getPlayerOrException();
		SharedInventoryState state = SharedInventoryState.get(source.getServer());
		String group = state.groupIdOf(self.getGameProfile().id());
		List<ServerPlayer> others = source.getServer().getPlayerList().getPlayers().stream()
				.filter(player -> player != self && state.groupIdOf(player.getGameProfile().id()).equals(group))
				.toList();
		if (others.isEmpty()) {
			throw NOBODY.create();
		}
		if (others.size() > 1) {
			throw AMBIGUOUS.create();
		}
		return others.getFirst();
	}

	public static int swap(ServerPlayer self, ServerPlayer other) throws CommandSyntaxException {
		if (self == other) {
			throw SELF.create();
		}
		// Un jugador en la pantalla de muerte sigue en PlayerList; teletransportarlo no lo revive.
		if (self.isDeadOrDying() || other.isDeadOrDying()) {
			throw DEAD.create();
		}

		Place selfPlace = Place.of(self);
		Place otherPlace = Place.of(other);
		otherPlace.moveHere(self);
		selfPlace.moveHere(other);

		self.sendSystemMessage(Component.literal("Intercambiaste lugar con ").append(other.getDisplayName()));
		other.sendSystemMessage(self.getDisplayName().copy().append(" intercambió lugar con vos"));
		return 1;
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
