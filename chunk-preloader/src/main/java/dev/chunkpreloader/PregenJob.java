package dev.chunkpreloader;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

public final class PregenJob {

    private final ChunkPreloaderPlugin plugin;
    private final World world;
    private final int minChunkX;
    private final int minChunkZ;
    private final int size;

    private long next;
    private final long total;
    private long completed;
    private final long completedAtStart;
    private final long startMillis;

    private int pending;
    private int outstanding;
    private BukkitTask task;
    private final AtomicBoolean finishing = new AtomicBoolean(false);

    private final File progressFile;

    PregenJob(ChunkPreloaderPlugin plugin, World world, int minChunkX, int minChunkZ, int size,
              long resumeNext, long resumeCompleted) {
        this.plugin = plugin;
        this.world = world;
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.size = size;
        this.total = (long) size * size;
        this.next = resumeNext;
        this.completed = resumeCompleted;
        this.completedAtStart = resumeCompleted;
        this.startMillis = System.currentTimeMillis();
        int cap = Math.max(1, plugin.getConfig().getInt("max-outstanding-chunks", 64));
        this.outstanding = plugin.getConfig().getBoolean("adaptive", true)
                ? Math.max(1, cap / 4)
                : cap;
        this.progressFile = new File(plugin.getDataFolder(), "progress/" + world.getName() + ".yml");
    }

    public World getWorld() {
        return world;
    }

    public long getCompleted() {
        return completed;
    }

    public long getTotal() {
        return total;
    }

    public boolean isWaitingForChunks() {
        return pending > 0;
    }

    public boolean hasMoreChunks() {
        return next < total;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("Pregen '" + world.getName() + "' : " + total + " chunks"
                + " (reprise a " + next + ")");
    }

    private void tick() {
        int cap = Math.max(1, plugin.getConfig().getInt("max-outstanding-chunks", 64));
        double minTps = plugin.getConfig().getDouble("min-tps", 17.0);
        boolean adaptive = plugin.getConfig().getBoolean("adaptive", true);
        double[] tps = Bukkit.getTPS();
        double tpsNow = tps.length > 0 ? tps[0] : 20.0;
        if (adaptive) {
            if (tpsNow < minTps) {
                outstanding = Math.max(1, outstanding / 2);
                if (tpsNow < minTps - 2.0) {
                    return;
                }
            } else if (tpsNow >= 19.5 && outstanding < cap) {
                outstanding = Math.min(cap, outstanding + 2);
            }
        } else if (tpsNow < minTps) {
            return;
        }
        while (pending < outstanding && next < total) {
            long idx = next++;
            int x = minChunkX + (int) (idx / size);
            int z = minChunkZ + (int) (idx % size);
            pending++;
            try {
                world.getChunkAtAsync(x, z, true).whenComplete((chunk, error) -> {
                    if (Bukkit.isPrimaryThread()) {
                        onComplete(chunk, error);
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> onComplete(chunk, error));
                    }
                });
            } catch (Exception e) {
                pending--;
                plugin.getLogger().warning("Chunk (" + x + ", " + z + ") impossible : " + e.getMessage());
            }
        }
        if (next >= total && pending <= 0) {
            finish(true);
        }
    }

    private void onComplete(Chunk chunk, Throwable error) {
        pending = Math.max(0, pending - 1);
        if (error != null) {
            plugin.getLogger().warning("Erreur de generation dans '" + world.getName() + "' : " + error.getMessage());
            return;
        }
        completed++;
        int saveEvery = plugin.getConfig().getInt("save-every-chunks", 50000);
        if (saveEvery > 0 && completed % saveEvery == 0) {
            world.save();
            plugin.getLogger().info("Pregen '" + world.getName() + "' : " + completed + "/" + total
                    + " chunks (" + percent() + "%) - monde sauvegarde");
        }
        if (completed % 5000 == 0 || completed == total) {
            saveProgress();
        }
        if (next >= total && pending <= 0) {
            finish(true);
        }
    }

    public void finish(boolean deleteProgress) {
        if (!finishing.compareAndSet(false, true)) {
            return;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
        saveProgress();
        if (deleteProgress) {
            world.save();
            if (progressFile.exists()) {
                progressFile.delete();
            }
            long elapsed = (System.currentTimeMillis() - startMillis) / 1000L;
            plugin.getLogger().info("Pregen '" + world.getName() + "' termine : " + completed
                    + " chunks en " + elapsed + "s. Monde sauvegarde.");
        }
    }

    public void saveProgress() {
        File parent = progressFile.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("size", size);
        yaml.set("min-chunk-x", minChunkX);
        yaml.set("min-chunk-z", minChunkZ);
        yaml.set("next", next);
        yaml.set("completed", completed);
        try {
            yaml.save(progressFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Sauvegarde de la progression impossible : " + e.getMessage());
        }
    }

    public static PregenJob loadOrCreate(ChunkPreloaderPlugin plugin, World world, int minChunkX, int minChunkZ, int size) {
        File file = new File(plugin.getDataFolder(), "progress/" + world.getName() + ".yml");
        long resumeNext = 0L;
        long resumeCompleted = 0L;
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.getInt("size", -1) == size
                    && yaml.getInt("min-chunk-x", Integer.MIN_VALUE) == minChunkX
                    && yaml.getInt("min-chunk-z", Integer.MIN_VALUE) == minChunkZ) {
                resumeNext = Math.max(0L, Math.min((long) size * size, yaml.getLong("next", 0L)));
                resumeCompleted = Math.max(0L, yaml.getLong("completed", 0L));
            }
        }
        return new PregenJob(plugin, world, minChunkX, minChunkZ, size, resumeNext, resumeCompleted);
    }

    public String percent() {
        return String.format(java.util.Locale.ROOT, "%.2f", total == 0 ? 100.0 : completed * 100.0 / total);
    }

    public String ratePerSecond() {
        long elapsed = Math.max(1L, (System.currentTimeMillis() - startMillis) / 1000L);
        long done = completed - completedAtStart;
        if (done <= 0) {
            return "0.0";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", (double) done / elapsed);
    }

    public String eta() {
        long elapsed = Math.max(1L, (System.currentTimeMillis() - startMillis) / 1000L);
        long done = completed - completedAtStart;
        long remaining = total - completed;
        if (done <= 0 || remaining <= 0) {
            return "?";
        }
        long seconds = remaining * elapsed / done;
        long h = seconds / 3600L;
        long m = (seconds % 3600L) / 60L;
        long s = seconds % 60L;
        return h > 0 ? h + "h " + m + "m" : (m > 0 ? m + "m " + s + "s" : s + "s");
    }
}
