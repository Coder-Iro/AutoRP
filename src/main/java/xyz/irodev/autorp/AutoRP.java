package xyz.irodev.autorp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URL;

public final class AutoRP extends JavaPlugin implements Listener {
    private final File configFile = new File(getDataFolder(), "config.yml");

    private String resourcePackURL = null;
    private String resourcePackHash = null;
    private Component resourcePackPrompt = null;

    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        var config = getConfig();
        applyResourcePack(config);

    }
    @EventHandler
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        var config = YamlConfiguration.loadConfiguration(configFile);
        applyResourcePack(config);
        return true;
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        if (resourcePackURL != null) {
            player.setResourcePack(resourcePackURL, resourcePackHash, true, resourcePackPrompt);
        }
    }
    private void applyResourcePack(FileConfiguration configuration) {
        var baseURL = configuration.getString("baseURL");
        var promptMessage = configuration.getString("prompt");

        if (promptMessage != null && !promptMessage.isBlank()) {
            resourcePackPrompt = legacySerializer.deserialize(promptMessage);
        } else {
            resourcePackPrompt = null;
        }

        if (baseURL != null) {
            try {
                var checksumURL = baseURL + "checksum.txt";
                var content = (new String(new URL(checksumURL).openConnection().getInputStream().readAllBytes())).split(" ");

                resourcePackURL = baseURL + content[1];
                resourcePackHash = content[0];

                for (Player player : getServer().getOnlinePlayers()) {
                    player.setResourcePack(resourcePackURL, resourcePackHash, true, resourcePackPrompt);
                }
            } catch (Exception exception) {
                resourcePackURL = null;
                resourcePackHash = null;

                exception.printStackTrace();
            }
        }
    }
}
