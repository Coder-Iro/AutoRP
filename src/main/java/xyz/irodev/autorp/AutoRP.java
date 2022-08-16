package xyz.irodev.autorp;

import at.favre.lib.bytes.Bytes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;

public final class AutoRP extends JavaPlugin implements Listener {
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration config;
    private final Logger logger = getSLF4JLogger();
    private String resourcePackURL = null;
    private String resourcePackHash = null;
    private Component resourcePackPrompt = null;
    private GithubWebhookThread webhookThread = null;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        config = getConfig();
        getServer().getPluginManager().registerEvents(this, this);
        applyResourcePack();

        var key = config.getString("webhook-key");
        var port = config.getInt("webhook-port");

        if (key != null) {
            webhookThread = new GithubWebhookThread(key, port, this::applyResourcePack);
            webhookThread.start();
        }
    }

    @Override
    public void onDisable() {
        webhookThread.stopServer();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        applyResourcePack();
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        if (resourcePackURL != null) {
            player.setResourcePack(resourcePackURL, resourcePackHash, true, resourcePackPrompt);
        }
    }

    private void applyResourcePack() {
        logger.info("ResourcePack update triggered");

        var baseURL = config.getString("baseURL");
        var promptMessage = config.getString("prompt");

        if (promptMessage != null && !promptMessage.isBlank()) {
            resourcePackPrompt = legacySerializer.deserialize(promptMessage);
        } else {
            resourcePackPrompt = null;
        }

        if (baseURL != null) {
            try {
                var checksumURL = MessageFormat.format("http://{0}/checksum.txt", baseURL);
                var content = (new String(new URL(checksumURL).openConnection().getInputStream().readAllBytes())).split(" ");


                if (resourcePackHash == null || !resourcePackHash.equals(content[0])) {
                    resourcePackURL = MessageFormat.format("http://{0}/{1}", baseURL, content[1]);
                    resourcePackHash = content[0];

                    // check sha1
                    var digest = MessageDigest.getInstance("SHA-1");

                    try (
                            var input = new URL(resourcePackURL).openStream();
                            var bis = new BufferedInputStream(input);
                            var dis = new DigestInputStream(bis, digest)
                    ) {
                        //noinspection StatementWithEmptyBody
                        while (dis.read() != -1) ;

                        var hash = digest.digest();

                        if (MessageDigest.isEqual(hash, Bytes.parseHex(resourcePackHash).array())) {
                            for (Player player : getServer().getOnlinePlayers()) {
                                player.setResourcePack(resourcePackURL, resourcePackHash, true, resourcePackPrompt);
                            }
                            logger.info("Resourcepack successfully updated");
                        } else {
                            logger.warn("SHA1 mismatch");
                        }
                    }
                } else logger.warn("Resourcepack doesn't changed. Reject resourcepack update");
            } catch (Exception exception) {
                resourcePackURL = null;
                resourcePackHash = null;
                logger.error("Exception occurred in applyResourcePack", exception);
            }
        } else logger.warn("baseURL is null");
    }
}
