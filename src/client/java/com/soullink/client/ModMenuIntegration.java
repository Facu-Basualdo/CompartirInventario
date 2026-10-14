package com.soullink.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** El botón de configuración en la lista de Mod Menu. Mod Menu es opcional: sin él, esta clase no se carga nunca. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SoulLinkConfigScreen::new;
	}
}
