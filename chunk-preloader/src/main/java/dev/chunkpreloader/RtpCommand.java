package dev.chunkpreloader;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class RtpCommand implements TabExecutor {

    private static final List<String> WORLDS = List.of("world", "nether");

    private final ChunkPreloaderPlugin plugin;
    private final RtpService rtpService;

    public RtpCommand(ChunkPreloaderPlugin plugin, RtpService rtpService) {
        this.plugin = plugin;
        this.rtpService = rtpService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Colors.PREFIX + "§cSeuls les joueurs peuvent utiliser /rtp.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Colors.PREFIX + "§cUsage : /rtp <world|nether> (l'end n'est pas pris en charge).");
            return true;
        }
        String arg = args[0].toLowerCase(Locale.ROOT);
        return plugin.worlds().byArgument(arg)
                .map(world -> {
                    rtpService.teleport(player, world, 1, false);
                    return true;
                })
                .orElseGet(() -> {
                    player.sendMessage(Colors.PREFIX + "§cDestination inconnue. Choisis : world ou nether.");
                    return true;
                });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return WORLDS.stream()
                    .filter(w -> w.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
