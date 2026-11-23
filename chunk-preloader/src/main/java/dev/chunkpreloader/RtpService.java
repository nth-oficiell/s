package dev.chunkpreloader;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class RtpService {

    private final ChunkPreloaderPlugin plugin;

    public RtpService(ChunkPreloaderPlugin plugin) {
        this.plugin = plugin;
    }

    public void teleport(Player player, World world, int attempt, boolean quiet) {
        if (attempt > plugin.getConfig().getInt("rtp.max-attempts", 12)) {
            player.sendMessage(Colors.PREFIX + "§cImpossible de trouver un endroit sur apres "
                    + plugin.getConfig().getInt("rtp.max-attempts", 12) + " essais. Reessaie plus tard.");
            plugin.getLogger().warning("RTP : echec pour " + player.getName() + " dans '" + world.getName() + "'");
            return;
        }

        int half = plugin.worlds().halfSize(world);
        int centerX = plugin.worlds().centerX();
        int centerZ = plugin.worlds().centerZ();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = centerX + (int) random.nextLong(-half, half + 1);
        int z = centerZ + (int) random.nextLong(-half, half + 1);

        if (!quiet && attempt == 1) {
            player.sendMessage(Colors.PREFIX + "§7Recherche d'un point d'arrivee (essai " + attempt
                    + ") dans §e" + world.getName() + "§7...");
        }

        world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, error) -> {
            Runnable action = () -> {
                if (error != null) {
                    teleport(player, world, attempt + 1, quiet);
                    return;
                }
                Location safe = findSafeLocation(world, x, z);
                if (safe == null) {
                    teleport(player, world, attempt + 1, quiet);
                    return;
                }
                float yaw = (float) (random.nextDouble() * 360.0);
                safe.setYaw(yaw);
                player.teleportAsync(safe).whenComplete((ok, teleportError) -> {
                    if (Boolean.TRUE.equals(ok)) {
                        player.sendMessage(Colors.PREFIX + "§aTeleporte en §e" + world.getName()
                                + "§7 : X §f" + safe.getBlockX()
                                + "§7, Y §f" + safe.getBlockY()
                                + "§7, Z §f" + safe.getBlockZ());
                    } else {
                        player.sendMessage(Colors.PREFIX + "§cTeleportation impossible, reessai...");
                        teleport(player, world, attempt + 1, quiet);
                    }
                });
            };
            if (Bukkit.isPrimaryThread()) {
                action.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, action);
            }
        });
    }

    private Location findSafeLocation(World world, int x, int z) {
        boolean nether = PregenManager.isNether(world);
        int top = world.getMaxHeight() - 1;
        int bottom = world.getMinHeight() + 1;
        int roofMargin = plugin.getConfig().getInt("rtp.nether-roof-margin", 10);
        int maxStandY = world.getMaxHeight() - Math.max(1, roofMargin);

        for (int y = top; y >= bottom; y--) {
            if (nether && y + 1 > maxStandY) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isSolid() || block.isLiquid()) {
                continue;
            }
            Block above = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);
            if (!above.isPassable() || above.isLiquid()) {
                continue;
            }
            if (!above2.isPassable() || above2.isLiquid()) {
                continue;
            }
            if (above.getType() == Material.LAVA || above2.getType() == Material.LAVA) {
                continue;
            }
            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }
        return null;
    }
}
