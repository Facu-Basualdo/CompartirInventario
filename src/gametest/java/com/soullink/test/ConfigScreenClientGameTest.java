package com.soullink.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import com.soullink.GroupStats;
import com.soullink.SoulLink;
import com.soullink.client.SoulLinkConfigScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.scores.DisplaySlot;

/**
 * La pantalla de configuración de Mod Menu, abierta directamente (Mod Menu no está en el entorno de prueba): la barra
 * lateral arranca vacía, y elegir las culpas desde la pantalla la muestra enseguida en el mundo abierto. Un número
 * fuera de rango no se guarda. Deja capturas en {@code build/run/clientGameTest/screenshots}.
 */
public class ConfigScreenClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// Los campos se buscan por su nombre en español: el cliente de prueba arranca en inglés
		context.runOnClient(client -> client.getLanguageManager().setSelected("es_ar"));
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.getInput().setCursorPos(0, 0);
			if (world.getServer().computeOnServer(server -> server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR) != null)) {
				throw new AssertionError("Por defecto la barra lateral tenía que arrancar vacía");
			}

			context.setScreen(() -> new SoulLinkConfigScreen(null));
			context.waitForScreen(SoulLinkConfigScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("config");
			// El único botón de opciones que arranca en "ninguna" es el de la barra lateral: el siguiente valor son las culpas
			context.runOnClient(client -> widgets(client, CycleButton.class).stream()
					.filter(button -> "ninguna".equals(button.getValue()))
					.findFirst()
					.orElseThrow()
					.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0)));
			context.runOnClient(client -> field(client, "Corazones de un susto").setValue("1,5"));
			context.clickScreenButton("Guardar");
			context.waitFor(client -> client.gui.screen() == null);
			boolean applied = world.getServer().computeOnServer(server -> SoulLink.config().sidebar().equals(Optional.of(GroupStats.Stat.FAULTS))
					&& SoulLink.config().scareHearts() == 1.5
					&& server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR) != null);
			if (!applied) {
				throw new AssertionError("Guardar tenía que poner las culpas en la barra lateral y 1.5 corazones de susto");
			}
			context.waitTicks(2);
			context.takeScreenshot("config-con-barra-lateral");

			// Más de 10 corazones: SoulLinkConfig avisa y usaría 2. No se guarda, y sigue 1.5
			context.setScreen(() -> new SoulLinkConfigScreen(null));
			context.waitForScreen(SoulLinkConfigScreen.class);
			context.runOnClient(client -> field(client, "Corazones de un susto").setValue("50"));
			context.clickScreenButton("Guardar");
			context.waitTicks(10);
			if (!context.computeOnClient(client -> client.gui.screen() instanceof SoulLinkConfigScreen)) {
				throw new AssertionError("Con un valor rechazado la pantalla tenía que quedar abierta, con el aviso");
			}
			context.takeScreenshot("config-rechazada");
			double hearts = world.getServer().computeOnServer(server -> SoulLink.config().scareHearts());
			if (hearts != 1.5) {
				throw new AssertionError("La config tenía que seguir con 1.5 corazones de susto, quedó " + hearts);
			}
			context.setScreen(() -> null);
		}
	}

	private static EditBox field(Minecraft client, String name) {
		return widgets(client, EditBox.class).stream()
				.filter(box -> box.getMessage().getString().equals(name))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No está el campo " + name));
	}

	/** Los de la lista están adentro del contenedor con scroll, no en la pantalla directamente. */
	private static <T> List<T> widgets(Minecraft client, Class<T> type) {
		List<T> found = new ArrayList<>();
		for (GuiEventListener child : client.gui.screen().children()) {
			visit(child, type, found);
		}
		return found;
	}

	private static <T> void visit(GuiEventListener element, Class<T> type, List<T> found) {
		if (element instanceof AbstractWidget && type.isInstance(element)) {
			found.add(type.cast(element));
		}
		if (element instanceof ContainerEventHandler container) {
			for (GuiEventListener child : container.children()) {
				visit(child, type, found);
			}
		}
	}
}
