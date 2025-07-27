package dev.imb11.skinshuffle;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.craftbukkit.entity.CraftPlayer;

import java.lang.reflect.InvocationTargetException;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkinShufflePlugin extends JavaPlugin implements Listener, PluginMessageListener {

    public static final String CBS = Bukkit.getServer().getClass().getPackage().getName();
    private static boolean debugMode = false;
    private static Logger logger;

    public static Class<?> bukkitClass(String clazz) throws ClassNotFoundException {
        return Class.forName(CBS + "." + clazz);
    }

    @Override
    public void onEnable() {
        logger = LoggerFactory.getLogger(getClass());

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "skinshuffle:handshake");
        getServer().getMessenger().registerIncomingPluginChannel(this, "skinshuffle:skin_refresh", this);
        getCommand("skinshuffle").setExecutor(new SkinShuffleCommand());

        if (debugMode) {
            logger.info("SkinShuffle plugin enabled");
        }
    }

    @Override
    public void onDisable() {
        if (debugMode) {
            logger.info("SkinShuffle plugin disabled");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (debugMode) {
            logger.info("Trying to send skinshuffle handshake to player: {}", event.getPlayer().getName());
        }

        getServer().getScheduler().runTaskLater(this, () -> {
            if (debugMode) {
                logger.info("Send packet!");
            }

            event.getPlayer().sendPluginMessage(this, "skinshuffle:handshake", new byte[0]);
        }, 20L);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (debugMode) {
            logger.info("Received plugin message from player: {}", player.getName());
        }

        if (channel.equals("skinshuffle:refresh") || channel.equals("skinshuffle:skin_refresh")) {
            if (debugMode) {
                logger.info("Received skin refresh message from player: {}", player.getName());
            }

            PlayerProfile playerProfile = player.getPlayerProfile();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));

            Property prop;
            if (buf.readBoolean()) {
                prop = new Property(buf.readUtf(), buf.readUtf(), buf.readUtf());
            } else {
                prop = new Property(buf.readUtf(), buf.readUtf(), null);
            }

            playerProfile.getProperties().removeIf(profileProperty -> profileProperty.getName().equals("textures"));
            playerProfile.getProperties().add(new ProfileProperty("textures", prop.value(), prop.signature()));
            player.setPlayerProfile(playerProfile);

            CraftPlayer craftPlayer = (CraftPlayer) player;

            try {
                var method = bukkitClass("entity.CraftPlayer").getDeclaredMethod("refreshPlayer");
                method.setAccessible(true);
                method.invoke(craftPlayer);

                try {
                    var triggerHealthUpdate = bukkitClass("entity.CraftPlayer").getDeclaredMethod("triggerHealthUpdate");
                    triggerHealthUpdate.setAccessible(true);
                    triggerHealthUpdate.invoke(craftPlayer);
                } catch (NoSuchMethodException e) {
                    player.resetMaxHealth();
                }
            } catch (NoSuchMethodException | ClassNotFoundException | InvocationTargetException | IllegalAccessException e) {
                logger.error("Failed to refresh player skin", e);
                throw new RuntimeException(e);
            }
        }
    }

    private static class SkinShuffleCommand implements CommandExecutor {
        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
            if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
                debugMode = !debugMode;

                sender.sendMessage("SkinShuffle debug: " + (debugMode ? "enabled" : "disabled"));
                return true;
            }
            return false;
        }
    }
}
