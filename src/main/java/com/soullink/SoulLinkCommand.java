package com.soullink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

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
 * config, sólo operadores. Los subcomandos existen en español y en inglés; los mensajes salen en el idioma
 * de quien lo usa (ver {@link Lang}).
 */
public final class SoulLinkCommand {
	/** Cuántas muertes del grupo muestra /soullink. */
	private static final int SHOWN_DEATHS = 3;
	/** Cuántos jugadores se pueden nombrar seguidos en /soullink grupo (más se pueden con un selector). */
	private static final int MAX_NAMED_PLAYERS = 8;

	/** Los nombres de los subcomandos en un idioma. {@code stats} es igual en los dos. */
	private record Names(String reset, String groups, String group, String remove, String reload) {
	}

	private static final List<Names> NAMES = List.of(
			new Names("reiniciar", "grupos", "grupo", "quitar", "recargar"),
			new Names("reset", "groups", "group", "remove", "reload"));

	private SoulLinkCommand() {
	}

	private interface PlayersAction {
		int run(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) throws CommandSyntaxException;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> stats = Commands.literal("stats")
				.executes(context -> show(context, ownGroup(context.getSource())))
				.then(Commands.argument("grupo", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(groupIds(context.getSource()), builder))
						.executes(context -> show(context, groupArgument(context))));
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(SoulLink.MOD_ID)
				.executes(context -> show(context, ownGroup(context.getSource())));

		for (Names names : NAMES) {
			stats.then(Commands.literal(names.reset())
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(SoulLinkCommand::reset));
			root.then(Commands.literal(names.groups())
							.executes(SoulLinkCommand::listGroups))
					.then(Commands.literal(names.group())
							.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
							.then(Commands.literal(names.remove())
									.then(players(1, (context, players) -> move(context, players, SharedInventoryState.GENERAL))))
							.then(Commands.argument("grupo", StringArgumentType.word())
									.suggests((context, builder) -> SharedSuggestionProvider.suggest(groupIds(context.getSource()), builder))
									.then(players(1, (context, players) -> move(context, players, groupArgument(context))))))
					.then(Commands.literal(names.reload())
							.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
							.executes(SoulLinkCommand::reload));
		}
		dispatcher.register(root.then(stats));
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
			throw new SimpleCommandExceptionType(Lang.tr(context.getSource(), "soullink.group.invalid", raw)).create();
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
		String language = Lang.of(source.getPlayer());
		MinecraftServer server = source.getServer();
		SharedInventoryState state = SharedInventoryState.get(server);
		if (!state.groups().containsKey(group)) {
			source.sendFailure(Lang.tr(language, "soullink.group.unknown", group));
			return 0;
		}
		GroupStats stats = GroupStats.get(server);
		GroupStats.Run run = stats.run(group);
		// Con un solo grupo, nombrarlo sólo confunde
		MutableComponent title = state.groups().size() > 1
				? Lang.tr(language, "soullink.stats.title_group", group, run.attempt())
				: Lang.tr(language, "soullink.stats.title", run.attempt());
		source.sendSuccess(() -> title.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Lang.tr(language, "soullink.stats.summary", GroupStats.formatTime(run.attemptTicks()),
				GroupStats.formatTime(run.bestTicks()), run.attempt() - 1).withStyle(ChatFormatting.GRAY), false);

		List<GroupStats.PlayerStats> ranking = stats.ranking(state, group);
		if (ranking.isEmpty()) {
			source.sendSuccess(() -> Lang.tr(language, "soullink.stats.empty"), false);
		}
		for (GroupStats.PlayerStats player : ranking) {
			source.sendSuccess(() -> playerLine(language, player), false);
		}

		List<GroupStats.Death> deaths = run.deaths();
		if (!deaths.isEmpty()) {
			source.sendSuccess(() -> Lang.tr(language, "soullink.stats.recent_deaths").withStyle(ChatFormatting.GRAY), false);
			for (GroupStats.Death death : deaths.subList(Math.max(0, deaths.size() - SHOWN_DEATHS), deaths.size()).reversed()) {
				source.sendSuccess(() -> Component.literal(" #" + death.attempt() + " (" + GroupStats.formatTime(death.ticks()) + ") ")
						.withStyle(ChatFormatting.DARK_GRAY)
						.append(death.cause().copy().withStyle(ChatFormatting.GRAY)), false);
			}
		}
		return ranking.size();
	}

	private static MutableComponent playerLine(String language, GroupStats.PlayerStats player) {
		return Component.literal(player.name()).withStyle(ChatFormatting.YELLOW)
				.append(Lang.tr(language, "soullink.stats.line",
						plural(language, "faults", GroupStats.Stat.FAULTS.of(player)),
						Lang.number(language, player.heartsLost()),
						plural(language, "scares", GroupStats.Stat.SCARES.of(player)),
						GroupStats.Stat.EXPERIENCE.of(player),
						plural(language, "mobs", GroupStats.Stat.MOBS.of(player))).withStyle(ChatFormatting.WHITE));
	}

	private static int listGroups(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String language = Lang.of(source.getPlayer());
		SharedInventoryState state = SharedInventoryState.get(source.getServer());
		Map<String, List<String>> assigned = state.assignedNames();
		source.sendSuccess(() -> Lang.tr(language, "soullink.groups.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		assigned.forEach((group, names) -> source.sendSuccess(() -> Component.literal(group + ": ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(String.join(", ", names)).withStyle(ChatFormatting.WHITE)), false));

		List<String> ungrouped = new ArrayList<>();
		for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
			if (state.groupIdOf(player.getGameProfile().id()).equals(SharedInventoryState.GENERAL)) {
				ungrouped.add(player.getGameProfile().name());
			}
		}
		ungrouped.sort(String.CASE_INSENSITIVE_ORDER);
		source.sendSuccess(() -> Lang.tr(language, "soullink.groups.general").withStyle(ChatFormatting.YELLOW)
				.append((ungrouped.isEmpty() ? Lang.tr(language, "soullink.groups.nobody") : Component.literal(String.join(", ", ungrouped)))
						.withStyle(ChatFormatting.WHITE)), false);
		if (assigned.isEmpty()) {
			source.sendSuccess(() -> Lang.tr(language, "soullink.groups.hint").withStyle(ChatFormatting.GRAY), false);
		}
		return assigned.size();
	}

	private static int move(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, String group) {
		boolean general = group.equals(SharedInventoryState.GENERAL);
		List<String> moved = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (LinkGroups.move(player, group)) {
				moved.add(player.getGameProfile().name());
				player.sendSystemMessage((general ? Lang.tr(player, "soullink.group.left") : Lang.tr(player, "soullink.group.joined", group))
						.withStyle(ChatFormatting.GOLD));
				player.sendSystemMessage(Lang.tr(player, "soullink.group.left_behind").withStyle(ChatFormatting.GRAY));
			}
		}
		CommandSourceStack source = context.getSource();
		if (moved.isEmpty()) {
			source.sendFailure(general ? Lang.tr(source, "soullink.group.already_none") : Lang.tr(source, "soullink.group.already", group));
			return 0;
		}
		String who = String.join(", ", moved);
		String number = moved.size() == 1 ? "one" : "many";
		source.sendSuccess(() -> general
				? Lang.tr(source, "soullink.group.removed." + number, who)
				: Lang.tr(source, "soullink.group.moved." + number, who, group), true);
		return moved.size();
	}

	private static int reset(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		GroupStats.get(server).reset();
		StatsScoreboard.reset(server);
		context.getSource().sendSuccess(() -> Lang.tr(context.getSource(), "soullink.stats.reset"), true);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		SoulLinkConfig.Reading reading = SoulLink.reloadConfig(source.getServer());
		// Después de recargar: si cambió el idioma de la config, la consola ya lo ve en el nuevo
		String language = Lang.of(source.getPlayer());
		reading.warnings().forEach(warning -> source.sendSuccess(() -> warning.in(language).copy().withStyle(ChatFormatting.YELLOW), false));
		if (!reading.ok()) {
			source.sendFailure(Lang.tr(language, "soullink.config.kept"));
			return 0;
		}
		source.sendSuccess(() -> reading.warnings().isEmpty()
				? Lang.tr(language, "soullink.config.reloaded")
				: Lang.tr(language, "soullink.config.reloaded_warnings", reading.warnings().size()), true);
		return 1;
	}

	private static Component plural(String language, String key, int count) {
		return count == 1 ? Lang.tr(language, "soullink.stats." + key + ".one") : Lang.tr(language, "soullink.stats." + key + ".many", count);
	}
}
