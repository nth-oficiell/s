package dev.chunkpreloader;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;

public final class Worlds {

    private final ChunkPreloaderPlugin plugin;

    public Worlds(ChunkPreloaderPlugin plugin) {
        this.plugin = plugin;
    }

    public World overworld() {
        FileConfiguration config = plugin.getConfig();
        String name = config.getString("worlds.overworld", "world");
        World world = name == null ? null : Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == Environment.NORMAL)
                .findFirst()
                .orElseGet(() -> Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
    }

    public World nether() {
        FileConfiguration config = plugin.getConfig();
        String name = config.getString("worlds.nether", "world_nether");
        World world = name == null ? null : Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == Environment.NETHER)
                .findFirst()
                .orElse(null);
    }

    public Optional<World> byArgument(String arg) {
        String lower = arg.toLowerCase();
        if (lower.equals("world") || lower.equals("overworld") || lower.equals("normal") || lower.equals("monde")) {
            return Optional.ofNullable(overworld());
        }
        if (lower.equals("nether") || lower.equals("enfer")) {
            return Optional.ofNullable(nether());
        }
        return Optional.empty();
    }

    public int halfSize(World world) {
        int half = Math.max(1, plugin.getConfig().getInt("overworld-half-size-blocks", 7500));
        if (world.getEnvironment() == Environment.NETHER) {
            int divisor = Math.max(1, plugin.getConfig().getInt("nether-divisor", 8));
            return Math.max(1, half / divisor);
        }
        return half;
    }

    public int centerX() {
        return plugin.getConfig().getInt("center.x", 0);
    }

    public int centerZ() {
        return plugin.getConfig().getInt("center.z", 0);
    }
}
