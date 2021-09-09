package org.bukkit.craftbukkit.generator;

import com.google.common.base.Preconditions;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftRegionAccessor;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Consumer;

public class CraftLimitedRegion extends CraftRegionAccessor implements LimitedRegion {

    private final WeakReference<WorldGenLevel> weakAccess;
    private final int centerChunkX;
    private final int centerChunkZ;
    // Buffer is one chunk (16 blocks), can be seen in ChunkStatus#q
    // there the order is {..., FEATURES, LIQUID_CARVERS, STRUCTURE_STARTS, ...}
    private final int buffer = 16;
    private final BoundingBox region;
    // Minecraft saves the entities as NBTTagCompound during chunk generation. This causes that
    // changes made to the returned bukkit entity are not saved. To combat this we keep them and
    // save them when the population is finished.
    private final List<net.minecraft.world.entity.Entity> entities = new ArrayList<>();

    public CraftLimitedRegion(WorldGenRegion access) {
        this.weakAccess = new WeakReference<>(access);
        centerChunkX = access.getCenter().x; // PAIL rename getCenter
        centerChunkZ = access.getCenter().z; // PAIL rename getCenter

        // load entities which are already present
        for (int x = -(buffer >> 4); x <= (buffer >> 4); x++) {
            for (int z = -(buffer >> 4); z <= (buffer >> 4); z++) {
                ProtoChunk chunk = (ProtoChunk) access.getChunk(centerChunkX + x, centerChunkZ + z);
                for (CompoundTag compound : chunk.getEntities()) {  // PAIL rename getGenerationEntities
                    net.minecraft.world.entity.EntityType.loadEntityRecursive(compound, access.getMinecraftWorld(), (entity) -> {  // PAIL rename fromNBTTag
                        entity.generation = true;
                        entities.add(entity);
                        return entity;
                    });
                }
            }
        }

        World world = access.getMinecraftWorld().getWorld();
        int xCenter = centerChunkX << 4;
        int zCenter = centerChunkZ << 4;
        int xMin = xCenter - getBuffer();
        int zMin = zCenter - getBuffer();
        int xMax = xCenter + getBuffer() + 16;
        int zMax = zCenter + getBuffer() + 16;

        this.region = new BoundingBox(xMin, world.getMinHeight(), zMin, xMax, world.getMaxHeight(), zMax);
    }

    public WorldGenLevel getHandle() {
        WorldGenLevel handle = weakAccess.get();

        if (handle == null) {
            throw new IllegalStateException("GeneratorAccessSeed no longer present, are you using it in a different tick?");
        }

        return handle;
    }

    public void saveEntities() {
        WorldGenLevel access = getHandle();
        for (int x = -(buffer >> 4); x <= (buffer >> 4); x++) {
            for (int z = -(buffer >> 4); z <= (buffer >> 4); z++) {
                ProtoChunk chunk = (ProtoChunk) access.getChunk(centerChunkX + x, centerChunkZ + z);
                chunk.getEntities().clear(); // PAIL rename getGenerationEntities
            }
        }

        for (net.minecraft.world.entity.Entity entity : entities) {
            if (entity.isAlive()) {
                // check if entity is still in region or if it got teleported outside it
                Preconditions.checkState(region.contains(entity.getX(), entity.getY(), entity.getZ()), "Entity %s is not in the region", entity);
                access.addFreshEntity(entity);
            }
        }
    }

    public void breakLink() {
        weakAccess.clear();
    }

    @Override
    public int getBuffer() {
        return buffer;
    }

    @Override
    public boolean isInRegion(Location location) {
        return region.contains(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public boolean isInRegion(int x, int y, int z) {
        return region.contains(x, y, z);
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        Preconditions.checkArgument(isInRegion(x, y, z), "Coordinates %s, %s, %s are not in the region", x, y, z);
        return super.getBiome(x, y, z);
    }

    @Override
    public void setBiome(int x, int y, int z, net.minecraft.world.level.biome.Biome biomeBase) {
        Preconditions.checkArgument(isInRegion(x, y, z), "Coordinates %s, %s, %s are not in the region", x, y, z);
        ChunkAccess chunk = getHandle().getChunk(x >> 4, z >> 4, ChunkStatus.EMPTY);
        chunk.getBiomes().setBiome(x >> 2, y >> 2, z >> 2, biomeBase);
    }

    @Override
    public BlockState getBlockState(int x, int y, int z) {
        Preconditions.checkArgument(isInRegion(x, y, z), "Coordinates %s, %s, %s are not in the region", x, y, z);
        return super.getBlockState(x, y, z);
    }

    @Override
    public BlockData getBlockData(int x, int y, int z) {
        Preconditions.checkArgument(isInRegion(x, y, z), "Coordinates %s, %s, %s are not in the region", x, y, z);
        return super.getBlockData(x, y, z);
    }

    @Override
    public Material getType(int x, int y, int z) {
        Preconditions.checkArgument(isInRegion(x, y, z), "Coordinates %s, %s, %s are not in the region", x, y, z);
        return super.getType(x, y, z);
    }

    @Override
    public void setBlockData(int x, int y, int z, BlockData blockData) {
        Preconditions.checkArgument(isInRegion(x, y, z), "Coordinates %s, %s, %s are not in the region", x, y, z);
        super.setBlockData(x, y, z, blockData);
    }

    @Override
    public boolean generateTree(Location location, Random random, TreeType treeType) {
        Preconditions.checkArgument(isInRegion(location), "Coordinates %s, %s, %s are not in the region", location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return super.generateTree(location, random, treeType);
    }

    @Override
    public boolean generateTree(Location location, Random random, TreeType treeType, Consumer<BlockState> consumer) {
        Preconditions.checkArgument(isInRegion(location), "Coordinates %s, %s, %s are not in the region", location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return super.generateTree(location, random, treeType, consumer);
    }

    @Override
    public Collection<net.minecraft.world.entity.Entity> getNMSEntities() {
        return new ArrayList<>(entities);
    }

    @Override
    public <T extends Entity> T spawn(Location location, Class<T> clazz, Consumer<T> function, CreatureSpawnEvent.SpawnReason reason) throws IllegalArgumentException {
        Preconditions.checkArgument(isInRegion(location), "Coordinates %s, %s, %s are not in the region", location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return super.spawn(location, clazz, function, reason);
    }

    @Override
    public void addEntityToWorld(net.minecraft.world.entity.Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        entities.add(entity);
    }
}
