package com.inventariocompartido;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Devuelve los ítems que un jugador tenía antes de que se instalara el mod. Al cargar su archivo se
 * rescataron (ver PlayerMixin) en vez de tirarse; acá se ponen en cofres al lado suyo. Si no hay lugar
 * para un cofre, quedan tirados a sus pies: nunca se pierden.
 */
public final class PersonalItemsChest {
	private static final int SEARCH_RADIUS = 3;

	private PersonalItemsChest() {
	}

	public static void placeRescuedItems(ServerPlayer player) {
		List<ItemStack> items = SharedInventoryState.get(player.level().getServer()).takeRescuedItems(player.getGameProfile().id());
		if (items.isEmpty()) {
			return;
		}

		ServerLevel level = player.level();
		List<BlockPos> chests = new ArrayList<>();
		int next = 0;
		while (next < items.size()) {
			BlockPos pos = findFreeSpot(level, player.blockPosition(), chests);
			if (pos == null) {
				break;
			}
			level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
			chests.add(pos);
			if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
				break;
			}
			for (int slot = 0; slot < chest.getContainerSize() && next < items.size(); slot++) {
				chest.setItem(slot, items.get(next++));
			}
		}

		boolean dropped = next < items.size();
		for (; next < items.size(); next++) {
			Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), items.get(next));
		}

		InventarioCompartido.LOGGER.info("Ítems de antes del mod de {} devueltos en {} cofre(s){}",
				player.getGameProfile().name(), chests.size(), dropped ? " y tirados a sus pies" : "");
		player.sendSystemMessage(Component.literal(message(chests, dropped)));
	}

	/**
	 * Primero a la altura de los pies y después una más arriba, de adentro hacia afuera. Nunca en la
	 * columna del jugador (lo encerraría) y nunca reemplazando un bloque sólido.
	 */
	private static @Nullable BlockPos findFreeSpot(ServerLevel level, BlockPos feet, List<BlockPos> taken) {
		for (int radius = 1; radius <= SEARCH_RADIUS; radius++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
							continue;
						}
						BlockPos pos = feet.offset(dx, dy, dz);
						if (level.isInWorldBounds(pos) && !taken.contains(pos) && level.getBlockState(pos).canBeReplaced()) {
							return pos;
						}
					}
				}
			}
		}
		return null;
	}

	private static String message(List<BlockPos> chests, boolean dropped) {
		StringBuilder text = new StringBuilder("[Inventario Compartido] Tus ítems de antes de instalar el mod ");
		if (!chests.isEmpty()) {
			text.append(chests.size() == 1 ? "están en el cofre de " : "están en los cofres de ");
			for (int i = 0; i < chests.size(); i++) {
				BlockPos pos = chests.get(i);
				text.append(i == 0 ? "" : " y ").append(pos.getX()).append(' ').append(pos.getY()).append(' ').append(pos.getZ());
			}
			if (dropped) {
				text.append("; lo que no entró quedó tirado a tus pies");
			}
		} else {
			text.append("quedaron tirados a tus pies (no había lugar para un cofre)");
		}
		return text.append('.').toString();
	}
}
