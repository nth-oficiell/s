package dev.chunkpreloader;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;

public final class PregenManager {

    private final ChunkPreloaderPlugin plugin;
    private final Map<String, PregenJob> jobs = new HashMap<>();

    public PregenManager(ChunkPreloaderPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean isRunning(World world) {
        PregenJob job = jobs.get(world.getName());
        return job != null && (job.hasMoreChunks() || job.isWaitingForChunks());
    }

    public synchronized boolean start(World world) {
        String key = world.getName();
        if (jobs.containsKey(key)) {
            return false;
        }
        int halfSize = plugin.worlds().halfSize(world);
        int centerX = plugin.worlds().centerX();
        int centerZ = plugin.worlds().centerZ();
        int minChunkX = floorDiv(centerX - halfSize, 16);
        int minChunkZ = floorDiv(centerZ - halfSize, 16);
        int maxChunkX = floorDiv(centerX + halfSize - 1, 16);
        int maxChunkZ = floorDiv(centerZ + halfSize - 1, 16);
        int size = Math.max(maxChunkX - minChunkX + 1, maxChunkZ - minChunkZ + 1);

        PregenJob job = PregenJob.loadOrCreate(plugin, world, minChunkX, minChunkZ, size);
        jobs.put(key, job);
        job.start();
        return true;
    }

    public synchronized boolean stop(World world) {
        PregenJob job = jobs.remove(world.getName());
        if (job == null) {
            return false;
        }
        job.finish(false);
        plugin.getLogger().info("Pregen '" + world.getName() + "' arrete a " + job.getCompleted()
                + "/" + job.getTotal() + " chunks (reprise possible).");
        return true;
    }

    public synchronized void shutdown() {
        for (String key : jobs.keySet().toArray(new String[0])) {
            World world = Bukkit.getWorld(key);
            PregenJob job = jobs.remove(key);
            if (job != null) {
                job.finish(false);
            }
            if (world != null) {
                world.save();
            }
        }
    }

    public synchronized void startAll() {
        World overworld = plugin.worlds().overworld();
        World nether = plugin.worlds().nether();
        if (overworld != null) {
            start(overworld);
        }
        if (nether != null) {
            start(nether);
        }
    }

    public Collection<PregenJob> getJobs() {
        return jobs.values();
    }

    private static int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }

    public static boolean isNether(World world) {
        return world.getEnvironment() == Environment.NETHER;
    }
}
