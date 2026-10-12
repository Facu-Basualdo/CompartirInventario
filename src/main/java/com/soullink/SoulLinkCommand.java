package com.soullink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * /soullink: estadísticas y grupos para cualquiera; armar grupos, reiniciar estadísticas y recargar la
 * config, sólo operadores.
 */
public final class SoulLinkCommand {
	/** Cuántas muertes del grupo muestra /soullink. */
	private static final int SHOWN_DEATHS = 3;
	/** Cuántos jugadores se pueden nombrar seguidos en /soullink grupo (más se pueden con un selector). */
	private static final int MAX_NAMED_PLAYERS = 8;

	private static final DynamicCommandExceptionType INVALID_GROUP = new DynamicCommandExceptionType(name -> Component.literal(
			"\"" + name + "\" no sirve como nombre de grupo: hasta 16 letras sin tildes, números, - o _"));

	private SoulLinkCommand() {
	}

	private interface PlayersAction {
		int run(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) throws CommandSyntaxException;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(SoulLink.MOD_ID)
				.executes(context -> show(context, ownGroup(context.getSource())))
				.then(Commands.literal("stats")
						.executes(context -> show(context, ownGroup(context.getSource())))
						.then(Commands.literal("reiniciar")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(SoulLinkCommand::reset))
						.then(Commands.argument("grupo", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(groupIds(context.getSource()), builder))
								.executes(context -> show(context, groupArgument(context)))))
				.then(Commands.literal("grupos")
						.executes(SoulLinkCommand::listGroups))
				.then(Commands.literal("grupo")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("quitar")
								.then(players(1, (context, players) -> move(context, players, SharedInventoryState.GENERAL))))
						.then(Commands.argument("grupo", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(groupIds(context.getSource()), builder))
								.then(players(1, (context, players) -> move(context, players, groupArgument(context))))))
				.then(Commands.literal("recargar")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SoulLinkCommand::reload)));
	}

	/**
	 * jugador1 [jugador2] … [jugador8]: cada uno acepta un nombre o un selector ({@code @a[team=rojo]}), y
	 * se puede cortar en cualquiera. Brigadier no tiene listas de largo variable, así que es una cadena.
	 */
	private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> players(int index, PlayersAction action) {
		RequiredArgumentBuilder<CommandSourceStack, EntitySelector> argument = Commands.argument("jugador" + index, EntityArgument.players())
				.executes(context -> {
					Set<ServerPlayer> players = new LinkedHashSet<>();
					for (int i = 1; i <= index; i++) {
						players.addAll(EntityArgument.getPlayers(context, "jugador" + i));
					}
					return action.run(context, players);
				});
		if (index < MAX_NAMED_PLAYERS) {
			argument.then(players(index + 1, action));
		}
		return argument;
	}

	private static String groupArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String raw = StringArgumentType.getString(context, "grupo");
		String id = LinkGroups.normalize(raw);
		if (id == null) {
			throw INVALID_GROUP.create(raw);
		}
		return id;
	}

	private static String ownGroup(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null ? SharedInventoryState.GENERAL : SharedInventoryState.get(source.getServer()).groupIdOf(player.getGameProfile().id());
	}

	private static Collection<String> groupIds(CommandSourceStack source) {
		return SharedInventoryState.get(source.getServer()).groups().keySet();
	}

	private static int show(CommandContext<CommandSourceStack> context, String group) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		SharedInventoryState state = SharedInventoryState.get(server);
		if (!state.groups().containsKey(group)) {
			source.sendFailure(Component.literal("No hay ningún grupo " + group + ": mirá los que hay con /soullink grupos"));
			return 0;
		}
		GroupStats stats = GroupStats.get(server);
		GroupStats.Run run = stats.run(group);
		// Con un solo grupo, nombrarlo sólo confunde
		String title = state.groups().size() > 1 ? "Soul Link · grupo " + group + " · intento #" : "Soul Link · intento #";
		source.sendSuccess(() -> Component.literal(title + run.attempt()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("Lleva " + GroupStats.formatTime(run.attemptTicks())
				+ " · récord " + GroupStats.formatTime(run.bestTicks())
				+ " · muertes del grupo: " + (run.attempt() - 1)).withStyle(ChatFormatting.GRAY), false);

		List<GroupStats.PlayerStats> ranking = stats.ranking(state, group);
		if (ranking.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Todavía no hay estadísticas"), false);
		}
		for (GroupStats.PlayerStats player : ranking) {
			source.sendSuccess(() -> playerLine(player), false);
		}

		List<GroupStats.Death> deaths = run.deaths();
		if (!deaths.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Últimas muertes:").withStyle(ChatFormatting.GRAY), false);
			for (GroupStats.Death death : deaths.subList(Math.max(0, deaths.size() - SHOWN_DEATHS), deaths.size()).reversed()) {
				source.sendSuccess(() -> Component.literal(" #" + death.attempt() + " (" + GroupStats.formatTime(death.ticks()) + ") ")
						.withStyle(ChatFormatting.DARK_GRAY)
						.append(death.cause().copy().withStyle(ChatFormatting.GRAY)), false);
			}
		}
		return ranking.size();
	}

	private static MutableComponent playerLine(GroupStats.PlayerStats player) {
		return Component.literal(player.name()).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(": "
						+ plural(GroupStats.Stat.FAULTS.of(player), "culpa", "culpas")
						+ " · " + hearts(player.heartsLost()) + " ❤ perdidos"
						+ " · " + plural(GroupStats.Stat.SCARES.of(player), "susto", "sustos")
						+ " · " + GroupStats.Stat.EXPERIENCE.of(player) + " de experiencia"
						+ " · " + plural(GroupStats.Stat.MOBS.of(player), "mob", "mobs")).withStyle(ChatFormatting.WHITE));
	}

	private static int listGroups(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		SharedInventoryState state = SharedInventoryState.get(source.getServer());
		Map<String, List<String>> assigned = state.assignedNames();
		source.sendSuccess(() -> Component.literal("Grupos vinculados").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		assigned.forEach((group, names) -> source.sendSuccess(() -> Component.literal(group + ": ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(String.join(", ", names)).withStyle(ChatFormatting.WHITE)), false));

		List<String> ungrouped = new ArrayList<>();
		for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
			if (state.groupIdOf(player.getGameProfile().id()).equals(SharedInventoryState.GENERAL)) {
				ungrouped.add(player.getGameProfile().name());
			}
		}
		ungrouped.sort(String.CASE_INSENSITIVE_ORDER);
		source.sendSuccess(() -> Component.literal(SharedInventoryState.GENERAL + " (los que no tienen grupo): ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(ungrouped.isEmpty() ? "nadie conectado" : String.join(", ", ungrouped)).withStyle(ChatFormatting.WHITE)), false);
		if (assigned.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Para separarlos: /soullink grupo <nombre> <jugadores>").withStyle(ChatFormatting.GRAY), false);
		}
		return assigned.size();
	}

	private static int move(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, String group) {
		List<String> moved = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (LinkGroups.move(player, group)) {
				moved.add(player.getGameProfile().name());
				player.sendSystemMessage(Component.literal(group.equals(SharedInventoryState.GENERAL)
						? "Saliste de tu grupo: ahora compartís todo con los que no tienen grupo"
						: "Ahora estás en el grupo " + group + ": compartís inventario, vida, hambre y experiencia con ellos")
						.withStyle(ChatFormatting.GOLD));
				player.sendSystemMessage(Component.literal("Lo que tenías quedó en tu grupo anterior").withStyle(ChatFormatting.GRAY));
			}
		}
		CommandSourceStack source = context.getSource();
		if (moved.isEmpty()) {
			source.sendFailure(Component.literal("Ya estaban en " + (group.equals(SharedInventoryState.GENERAL) ? "ningún grupo" : "el grupo " + group)));
			return 0;
		}
		String who = String.join(", ", moved);
		source.sendSuccess(() -> Component.literal(group.equals(SharedInventoryState.GENERAL)
				? who + " ya no " + (moved.size() == 1 ? "tiene" : "tienen") + " grupo"
				: who + " ahora " + (moved.size() == 1 ? "está" : "están") + " en el grupo " + group), true);
		return moved.size();
	}

	private static int reset(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		GroupStats.get(server).reset();
		StatsScoreboard.reset(server);
		context.getSource().sendSuccess(() -> Component.literal("Las estadísticas de Soul Link volvieron a cero"), true);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		SoulLinkConfig.Reading reading = SoulLink.reloadConfig(source.getServer());
		reading.warnings().forEach(warning -> source.sendSuccess(() -> Component.literal(warning).withStyle(ChatFormatting.YELLOW), false));
		if (!reading.ok()) {
			source.sendFailure(Component.literal("Se sigue usando la config anterior"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Config recargada" + (reading.warnings().isEmpty() ? "" : " (con " + reading.warnings().size() + " avisos)")), true);
		return 1;
	}

	private static String plural(int count, String one, String many) {
		return count + " " + (count == 1 ? one : many);
	}

	/** Con medio corazón de precisión, y coma decimal: 17,5. */
	static String hearts(float hearts) {
		float rounded = Math.round(hearts * 2) / 2.0F;
		return rounded == (int) rounded ? Integer.toString((int) rounded) : Float.toString(rounded).replace('.', ',');
	}
}
