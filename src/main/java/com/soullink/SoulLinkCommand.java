package com.soullink;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * /soullink: estadísticas del grupo para cualquiera; reiniciarlas y recargar la config, sólo operadores.
 */
public final class SoulLinkCommand {
	/** Cuántas muertes del grupo muestra /soullink. */
	private static final int SHOWN_DEATHS = 3;

	private SoulLinkCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(SoulLink.MOD_ID)
				.executes(SoulLinkCommand::show)
				.then(Commands.literal("stats")
						.executes(SoulLinkCommand::show)
						.then(Commands.literal("reiniciar")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(SoulLinkCommand::reset)))
				.then(Commands.literal("recargar")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(SoulLinkCommand::reload)));
	}

	private static int show(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		GroupStats stats = GroupStats.get(source.getServer());
		source.sendSuccess(() -> Component.literal("Soul Link · intento #" + stats.attempt()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.literal("Lleva " + GroupStats.formatTime(stats.attemptTicks())
				+ " · récord " + GroupStats.formatTime(stats.bestTicks())
				+ " · muertes del grupo: " + (stats.attempt() - 1)).withStyle(ChatFormatting.GRAY), false);

		List<GroupStats.PlayerStats> ranking = stats.ranking();
		if (ranking.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Todavía no hay estadísticas"), false);
		}
		for (GroupStats.PlayerStats player : ranking) {
			source.sendSuccess(() -> playerLine(player), false);
		}

		List<GroupStats.Death> deaths = stats.deaths();
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
