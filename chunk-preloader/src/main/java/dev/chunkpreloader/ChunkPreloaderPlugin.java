package dev.chunkpreloader;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkPreloaderPlugin extends JavaPlugin {

    private PregenManager pregenManager;
    private Worlds worlds;
    private RtpService rtpService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worlds = new Worlds(this);
        pregenManager = new PregenManager(this);
        rtpService = new RtpService(this);

        PluginCommand preload = getCommand("preload");
        if (preload != null) {
            PreloadCommand executor = new PreloadCommand(this);
            preload.setExecutor(executor);
            preload.setTabCompleter(executor);
        }
        PluginCommand rtp = getCommand("rtp");
        if (rtp != null) {
            RtpCommand executor = new RtpCommand(this, rtpService);
            rtp.setExecutor(executor);
            rtp.setTabCompleter(executor);
        }

        if (getConfig().getBoolean("auto-start", false)) {
            getServer().getScheduler().runTask(this, pregenManager::startAll);
        }
        getLogger().info("ChunkPreloader actif. /preload start pour lancer la pregeneration.");
    }

    @Override
    public void onDisable() {
        if (pregenManager != null) {
            pregenManager.shutdown();
        }
    }

    public PregenManager pregen() {
        return pregenManager;
    }

    public Worlds worlds() {
        return worlds;
    }

    public RtpService rtp() {
        return rtpService;
    }
}
