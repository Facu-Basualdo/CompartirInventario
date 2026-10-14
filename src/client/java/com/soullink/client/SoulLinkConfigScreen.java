package com.soullink.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.soullink.GroupStats;
import com.soullink.Lang;
import com.soullink.SoulLink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * {@code config/soullink.json} desde Mod Menu. Con un mundo abierto en esta instancia se aplica como
 * {@code /soullink recargar}: la barra lateral cambia enseguida. La pantalla en sí es la de
 * {@link PantallaDeConfiguracion}, copiada igual en los cuatro mods: por eso está en español.
 */
public final class SoulLinkConfigScreen extends PantallaDeConfiguracion {
	public SoulLinkConfigScreen(@Nullable Screen parent) {
		super(Lang.tr(language(), "soullink.config.pantalla.titulo"), parent, SoulLink.configFile());
	}

	private static String language() {
		return Lang.resolve(Minecraft.getInstance().getLanguageManager().getSelected());
	}

	@Override
	protected @Nullable MutableComponent traducir(String key, Object... args) {
		String language = language();
		return Lang.keys(language).contains(key) ? Lang.tr(language, key, args) : null;
	}

	@Override
	protected String prefijo() {
		return "soullink.config";
	}

	@Override
	protected List<Component> recargar(@Nullable MinecraftServer server) {
		String language = language();
		return SoulLink.reloadConfig(server).warnings().stream().map(warning -> warning.in(language)).toList();
	}

	@Override
	protected List<String> opciones(String path) {
		if (path.equals("idioma")) {
			return Lang.AVAILABLE;
		}
		if (path.equals("barraLateral")) {
			List<String> ids = new ArrayList<>(List.of("ninguna"));
			Arrays.stream(GroupStats.Stat.values()).map(GroupStats.Stat::id).forEach(ids::add);
			return ids;
		}
		return List.of();
	}

	/** SoulLinkConfig lo lee como double y escribe 2 en vez de 2.0: sin esto, la pantalla no dejaría poner 1.5. */
	@Override
	protected boolean admiteDecimales(String path) {
		return path.equals("corazonesDeSusto");
	}
}
