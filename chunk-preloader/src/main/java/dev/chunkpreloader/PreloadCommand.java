package dev.chunkpreloader;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

public final class PreloadCommand implements TabExecutor {

    private static final List<String> ACTIONS = List.of("start", "stop", "status");
    private static final List<String> WORLDS = List.of("world", "nether");

    private final ChunkPreloaderPlugin plugin;

    public PreloadCommand(ChunkPreloaderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Colors.PREFIX + "§cUsage : /preload <start|stop|status> [world|nether]");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("status")) {
            sendStatus(sender);
            return true;
        }
        if (!action.equals("start") && !action.equals("stop")) {
            sender.sendMessage(Colors.PREFIX + "§cUsage : /preload <start|stop|status> [world|nether]");
            return true;
        }

        boolean allWorlds = args.length < 2;
        String worldArg = allWorlds ? null : args[1].toLowerCase(Locale.ROOT);
        boolean worldSelected = worldArg == null || worldArg.equals("world") || worldArg.equals("overworld") || worldArg.equals("normal");
        boolean netherSelected = worldArg == null || worldArg.equals("nether") || worldArg.equals("enfer");

        if (!worldSelected && !netherSelected) {
            sender.sendMessage(Colors.PREFIX + "§cMonde inconnu. Choisis : world ou nether (end non pris en charge).");
            return true;
        }

        if (action.equals("start")) {
            start(sender, allWorlds, worldSelected, netherSelected);
        } else {
            stop(sender, allWorlds, worldSelected, netherSelected);
        }
        return true;
    }

    private void start(CommandSender sender, boolean allWorlds, boolean worldSelected, boolean netherSelected) {
        if (worldSelected && !allWorlds) {
            startOne(sender, plugin.worlds().overworld(), "monde normal");
        }
        if (netherSelected && !allWorlds) {
            startOne(sender, plugin.worlds().nether(), "nether");
        }
        if (allWorlds) {
            startOne(sender, plugin.worlds().overworld(), "monde normal");
            startOne(sender, plugin.worlds().nether(), "nether");
        }
    }

    private void startOne(CommandSender sender, World world, String label) {
        if (world == null) {
            sender.sendMessage(Colors.PREFIX + "§cIntrouvable : " + label);
            return;
        }
        int halfSize = plugin.worlds().halfSize(world);
        int side = halfSize * 2;
        if (plugin.pregen().isRunning(world)) {
            sender.sendMessage(Colors.PREFIX + "§7Pregen deja en cours pour §e" + world.getName() + "§7. "
                    + "Utilise §f/preload status§7.");
            return;
        }
        plugin.pregen().start(world);
        sender.sendMessage(Colors.PREFIX + "§aPregen lancee : §e" + world.getName() + "§a sur §e"
                + side + " x " + side + "§a blocs (centre " + plugin.worlds().centerX() + ", "
                + plugin.worlds().centerZ() + "). La reprise apres redemarrage est automatique.");
    }

    private void stop(CommandSender sender, boolean allWorlds, boolean worldSelected, boolean netherSelected) {
        if (worldSelected) {
            stopOne(sender, plugin.worlds().overworld(), "monde normal");
        }
        if (netherSelected) {
            stopOne(sender, plugin.worlds().nether(), "nether");
        }
    }

    private void stopOne(CommandSender sender, World world, String label) {
        if (world == null) {
            sender.sendMessage(Colors.PREFIX + "§cIntrouvable : " + label);
            return;
        }
        if (!plugin.pregen().stop(world)) {
            sender.sendMessage(Colors.PREFIX + "§7Aucune pregen en cours pour §e" + world.getName() + "§7.");
        } else {
            sender.sendMessage(Colors.PREFIX + "§aPregen arretee pour §e" + world.getName()
                    + "§a. Progression sauvegardee, reprise possible.");
        }
    }

    private void sendStatus(CommandSender sender) {
        List<PregenJob> jobs = List.copyOf(plugin.pregen().getJobs());
        if (jobs.isEmpty()) {
            sender.sendMessage(Colors.PREFIX + "§7Aucune pregen en cours. §f/preload start [world|nether]");
        }
        for (PregenJob job : jobs) {
            sender.sendMessage(Colors.PREFIX + "§e" + job.getWorld().getName() + "§7 : §f" + job.getCompleted()
                    + "§7/§f" + job.getTotal() + "§7 chunks (§a" + job.percent() + "%§7, "
                    + job.ratePerSecond() + " chunks/s, ETA " + job.eta() + ")");
        }
        World overworld = plugin.worlds().overworld();
        World nether = plugin.worlds().nether();
        if (overworld != null) {
            int half = plugin.worlds().halfSize(overworld);
            sender.sendMessage(Colors.PREFIX + "§7Zone cible monde normal : §f" + (half * 2) + " x " + (half * 2)
                    + "§7 blocs autour de " + plugin.worlds().centerX() + ", " + plugin.worlds().centerZ());
        }
        if (nether != null) {
            int half = plugin.worlds().halfSize(nether);
            sender.sendMessage(Colors.PREFIX + "§7Zone cible nether (÷" + plugin.getConfig().getInt("nether-divisor", 8)
                    + ") : §f" + (half * 2) + " x " + (half * 2) + "§7 blocs");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return ACTIONS.stream()
                    .filter(a -> a.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return WORLDS.stream()
                    .filter(w -> w.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
