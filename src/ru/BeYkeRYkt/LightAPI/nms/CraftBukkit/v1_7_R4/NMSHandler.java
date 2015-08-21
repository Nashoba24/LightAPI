package ru.BeYkeRYkt.LightAPI.nms.CraftBukkit.v1_7_R4;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.server.v1_7_R4.Chunk;
import net.minecraft.server.v1_7_R4.EntityPlayer;
import net.minecraft.server.v1_7_R4.EnumSkyBlock;
import net.minecraft.server.v1_7_R4.PacketPlayOutMapChunk;
import net.minecraft.server.v1_7_R4.WorldServer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_7_R4.CraftWorld;

import ru.BeYkeRYkt.LightAPI.ChunkCoord;
import ru.BeYkeRYkt.LightAPI.nms.INMSHandler;

public class NMSHandler implements INMSHandler {

	private static BlockFace[] SIDES = { BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
	private static Field cachedChunkModified;

	@Override
	public void createLight(Location location, int light) {
		WorldServer world = ((CraftWorld) location.getWorld()).getHandle();
		world.b(EnumSkyBlock.BLOCK, location.getBlockX(), location.getBlockY(), location.getBlockZ(), light);

		Block adjacent = getAdjacentAirBlock(location.getBlock());
		recalculateBlockLighting(location.getWorld(), adjacent.getX(), adjacent.getY(), adjacent.getZ());
	}

	@Override
	public void deleteLight(Location location) {
		recalculateBlockLighting(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	@Override
	public List<ChunkCoord> collectChunks(Location location) {
		List<ChunkCoord> list = new CopyOnWriteArrayList<ChunkCoord>();
		try {
			WorldServer nmsWorld = ((CraftWorld) location.getChunk().getWorld()).getHandle();
			for (int dX = -1; dX <= 1; dX++) {
				for (int dZ = -1; dZ <= 1; dZ++) {
					if (nmsWorld.chunkProviderServer.isChunkLoaded(location.getChunk().getX() + dX, location.getChunk().getZ() + dZ)) {
						Chunk chunk = nmsWorld.getChunkAt(location.getChunk().getX() + dX, location.getChunk().getZ() + dZ);
						Field isModified = getChunkField(chunk);
						if (isModified.getBoolean(chunk)) {
							ChunkCoord cCoord = new ChunkCoord(location.getWorld(), chunk.locX, chunk.locZ);
							list.add(cCoord);
							isModified.setBoolean(chunk, false);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	public void recalculateBlockLighting(World world, int x, int y, int z) {
		WorldServer nmsWorld = ((CraftWorld) world).getHandle();
		nmsWorld.c(EnumSkyBlock.BLOCK, x, y, z);
	}

	public Block getAdjacentAirBlock(Block block) {
		for (BlockFace face : SIDES) {
			if (block.getY() == 0x0 && face == BlockFace.DOWN)
				continue;
			if (block.getY() == 0xFF && face == BlockFace.UP)
				continue;

			Block candidate = block.getRelative(face);

			if (candidate.getType().isTransparent()) {
				return candidate;
			}
		}
		return block;
	}

	private static Field getChunkField(Object chunk) throws NoSuchFieldException, SecurityException {
		if (cachedChunkModified == null) {
			cachedChunkModified = chunk.getClass().getDeclaredField("n");
			cachedChunkModified.setAccessible(true);
		}
		return cachedChunkModified;
	}

	private void sendPacket(Chunk chunk) {
		for (Object human : chunk.world.players) {
			EntityPlayer player = (EntityPlayer) human;
			Chunk pChunk = player.world.getChunkAtWorldCoords(player.getChunkCoordinates().x, player.getChunkCoordinates().z);
			if (distanceTo(pChunk, chunk) < 5) {
				PacketPlayOutMapChunk packet = new PacketPlayOutMapChunk(chunk, false, 65535);
				player.playerConnection.sendPacket(packet);
			}
		}
	}

	public int distanceTo(Chunk from, Chunk to) {
		if (!from.world.getWorldData().getName().equals(to.world.getWorldData().getName()))
			return 100;
		double var2 = to.locX - from.locX;
		double var4 = to.locZ - from.locZ;
		return (int) Math.sqrt(var2 * var2 + var4 * var4);
	}

	@Override
	public void updateChunk(ChunkCoord cCoord) {
		Chunk chunk = ((CraftWorld) cCoord.getWorld()).getHandle().getChunkAt(cCoord.getX(), cCoord.getZ());
		sendPacket(chunk);
	}
}
