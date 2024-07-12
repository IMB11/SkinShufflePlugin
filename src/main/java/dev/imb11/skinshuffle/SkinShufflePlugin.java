package dev.imb11.skinshuffle;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.properties.Property;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*? if <1.20.4 {*//*
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
*//*?} else {*/
    /*? if <1.20.6 {*/
    /*import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
    *//*?} else {*/
    import org.bukkit.craftbukkit.entity.CraftPlayer;
    /*?}*/
/*?}*/

import java.lang.reflect.InvocationTargetException;

public final class SkinShufflePlugin extends JavaPlugin implements Listener, PluginMessageListener {
    public final static String CBS = Bukkit.getServer().getClass().getPackage().getName();
    public final static Logger LOGGER = LoggerFactory.getLogger(SkinShufflePlugin.class);

    public static Class<?> bukkitClass(String clazz) throws ClassNotFoundException {
        return Class.forName(CBS + "." + clazz);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        LOGGER.info("SkinShuffle plugin enabled");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "skinshuffle:handshake");

        /*? if <1.20.6 {*//*
        getServer().getMessenger().registerIncomingPluginChannel(this, "skinshuffle:refresh", this);
        *//*?} else {*/
        getServer().getMessenger().registerIncomingPluginChannel(this, "skinshuffle:skin_refresh", this);
        /*?}*/

//        getServer().getMessenger().registerIncomingPluginChannel(this, "skinshuffle:refresh_player_list_entry", this);
        // Don't need player list refresh, handled by paper.
    }

    @Override
    public void onDisable() {}

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        LOGGER.info("Trying to send skinshuffle handshake to player: {}", event.getPlayer().getName());
        // Wait for the player to be ready to receive the handshake
        getServer().getScheduler().runTaskLater(this, () -> {
            LOGGER.info("Send packet!");
            event.getPlayer().sendPluginMessage(this, "skinshuffle:handshake", new byte[0]);
        }, 20L);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        LOGGER.info("Received plugin message from player: {}", player.getName());
        if(channel.equals("skinshuffle:refresh") || channel.equals("skinshuffle:skin_refresh")) {
            LOGGER.info("Received skin refresh message from player: {}", player.getName());
            PlayerProfile playerProfile = player.getPlayerProfile();
            // Get profileProperty from message.
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));

            /*? if <1.20.6 {*//*
            Property prop = buf.readProperty();
            *//*?} else {*/
            Property prop;
            if(buf.readBoolean()) {
                prop = new Property(buf.readUtf(), buf.readUtf(), buf.readUtf());
            } else {
                prop = new Property(buf.readUtf(), buf.readUtf(), null);
            }
            /*?}*/

            playerProfile.getProperties().removeIf(profileProperty -> profileProperty.getName().equals("textures"));

            /*? if <1.20.4 {*//*
            playerProfile.getProperties().add(new ProfileProperty("textures", prop.getValue(), prop.getSignature()));
            *//*?} else {*/
            playerProfile.getProperties().add(new ProfileProperty("textures", prop.value(), prop.signature()));
            /*?}*/

            player.setPlayerProfile(playerProfile);
            CraftPlayer craftPlayer = (CraftPlayer) player;
            try {
                var method = bukkitClass("entity.CraftPlayer").getDeclaredMethod("refreshPlayer");
                method.setAccessible(true);
                method.invoke(craftPlayer);

                // Also attempt to call org.bukkit.entity.Player#triggerHealthUpdate
                // if fail, just use player.resetMaxHealth();
                // fix XP on old paper versions (might not be an issue anymore)
                try {
                    var triggerHealthUpdate = bukkitClass("entity.CraftPlayer").getDeclaredMethod("triggerHealthUpdate");
                    triggerHealthUpdate.setAccessible(true);
                    triggerHealthUpdate.invoke(craftPlayer);
                } catch (NoSuchMethodException e) {
                    player.resetMaxHealth();
                }
            } catch (NoSuchMethodException | ClassNotFoundException | InvocationTargetException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
