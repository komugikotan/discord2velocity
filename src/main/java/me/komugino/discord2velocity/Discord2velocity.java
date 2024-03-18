package me.komugino.discord2velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EventListener;
import java.util.Map;
import java.util.Objects;


@Plugin(
        id = "discord2velocity",
        name = "discord2velocity",
        version = BuildConstants.VERSION,
        description = "This plugin allows you to connect Discord and Velocity server together.",
        authors = {"Komugikotan"}
)
public class Discord2velocity extends ListenerAdapter {

    @Inject
    private Logger logger;
    private String token, channelName;
    private TextChannel textChannel;
    private ProxyServer server;
    private Map config;

    @Inject
    public Discord2velocity(ProxyServer server) {
        this.server = server;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Discord2Velocity has started initializing.");

        //Check if yaml file exists
        //If not, create one
        Yaml y = new Yaml();
        File f = new File("./plugins/Discord2Velocity/config.yaml");
        if(!f.exists()) {
            logger.info("config.yaml not found. Creating one...");
            try {
                Path p = Paths.get("./plugins/Discord2Velocity");
                Files.createDirectory(p);
                f.createNewFile();

                //Write default config
                FileWriter fw = new FileWriter(f);
                fw.write("# General settings\n" +
                        "token: \"Token of your discord bot.\"\n" +
                        "channel_name: \"Name of the channel where the messages will be sent.\"\n" +
                        "\n" +
                        "# Messages shown in the game or discord chat channel\n" +
                        "proxy_server_started: \"# Proxy server has started.\"\n" +
                        "proxy_server_stopped: \"# Proxy server has stopped.\"\n" +
                        "message_sent_to_discord: \"[%server_name%]%player_name%:%message%\"\n" +
                        "message_sent_to_minecraft: \"§9(Discord)%player_name%:§f%message%\"\n" +
                        "player_joined: \"```%player_name% joined the %server_name% server.```\"\n" +
                        "player_left: \"```%player_name% left the proxy server.```\"");
                fw.flush();

                logger.info("config.yaml has been created. Please edit the file and restart the server.");
            } catch (Exception e) {
                logger.error("Failed to create config.yaml. Please create one manually.");
            }
        }
        else{
            try (final InputStream in = Files.newInputStream(Paths.get("./plugins/Discord2Velocity/config.yaml"))) {
                config= y.loadAs(in, Map.class);
                token = (String) config.get("token");
                channelName = (String) config.get("channel_name");
            }
            catch (Exception e){
                logger.error("Failed to load config.yaml. Please check if the file is corrupted.");
            }
        }

        logger.info("Discord2Velocity has finished initializing. Trying to connect to Discord...");

        //Login to Discord
        JDA jda = null;
        if(Objects.equals(token, "Token of your discord bot.") || token == null || token.equals("")){
            logger.error("Token not found. Please add your bot token to config.yaml.");
            return;
        }
        else{
            jda = JDABuilder.createDefault(token)
                    .setRawEventsEnabled(true)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(this)
                    .setActivity(Activity.playing("Minecraft"))
                    .build();
        }

        //Wait until JDA is ready
        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            logger.error("Failed to start JDA. Please check if the token is correct.");
            return;
        }


        try {
            textChannel = jda.getTextChannelsByName(channelName, true).get(0);
        }
        catch (Exception e){
            logger.error("Failed to find channel. Please check if the channel name is correct.");
            return;
        }

        if(textChannel == null){
            logger.error("Channel not found. Please check if the channel name is correct.");
            return;
        }
        else{
            logger.info("Channel found. Sending start message...");
            textChannel.sendMessage((String) config.get("proxy_server_started")).queue();
        }
    }

    @Subscribe
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();

        if (message.equals("!status")) {
            textChannel.sendMessage("Working!").queue();
        }
        else if(message.startsWith("!")){
            event.getChannel().sendMessage("Unknown command.").queue();
        }
        else if(Objects.equals(event.getChannel().toString(), textChannel.toString())){
            String mcid = event.getAuthor().getName();
            String rawMessage = config.get("message_sent_to_minecraft")
                    .toString()
                    .replace("%player_name%", mcid)
                    .replace("%message%", message);

            //Broadcast message to all backend servers of veloticy netowrk
            broadcastToAllServers(rawMessage);

            //Log
            logger.info(rawMessage);
        }
        else{
            logger.error("Unknown channel. Please check if the channel name is correct. The Channel name was:" + event.getChannel().getName() + " but it suppose to be a " + textChannel.getName());
        }

    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event){
        //Check if the bot is connected
        if(textChannel != null){
            //Server name and player name
            Player p = event.getPlayer();
            String mcid = p.getUsername();
            String server = p.getCurrentServer().get().getServerInfo().getName();

            String rawMessage = config.get("message_sent_to_discord")
                    .toString()
                    .replace("%server_name%", server)
                    .replace("%player_name%", mcid)
                    .replace("%message%", event.getMessage());
            //Send message to Discord
            this.textChannel.sendMessage(rawMessage).queue();
            logger.info("Message sent to Discord: " + rawMessage);
        }
        else{
            logger.error("Bot not connected. Please check if the bot is running.");
        }
    }

    @Subscribe
    public void proxyShutDown(ProxyShutdownEvent e) throws InterruptedException {
        //Check if the bot is connected
        if(textChannel != null){
            textChannel.sendMessage((String) config.get("proxy_server_stopped")).queue();
        }
        else{
            logger.error("Bot not connected. Please check if the bot is running.");
        }

        logger.info("Discord2Velocity has been disabled.");

        wait(1000);
    }

    @Subscribe
    public void playerJoin(ServerConnectedEvent e) {
        //Check if the bot is connected
        if(textChannel != null){
            Player p = e.getPlayer();
            String server = e.getServer().getServerInfo().getName();
            String mcid = p.getUsername();

            String rawMessage = config.get("player_joined").toString()
                    .replace("%server_name%", server)
                    .replace("%player_name%", mcid);

            //Send message to Discord
            textChannel.sendMessage(rawMessage).queue();
        }
        else{
            logger.error("Bot not connected. Please check if the bot is running.");
        }
    }

    @Subscribe
    public void playerLeft(DisconnectEvent e){
        //Check if the bot is connected
        if(textChannel != null){
            Player p = e.getPlayer();
            String mcid = p.getUsername();

            String rawMessage = config.get("player_left").toString()
                    .replace("%player_name%", mcid);

            //Send message to Discord
            textChannel.sendMessage(rawMessage).queue();
        }
        else{
            logger.error("Bot not connected. Please check if the bot is running.");
        }
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (event.getCommand().equals("velo2disc reload")) {
            CommandSource source = event.getCommandSource();
            if (source.hasPermission("velo2disc.reload")) {
                // Reload the config
                Yaml y = new Yaml();
                try (final InputStream in = Files.newInputStream(Paths.get("./plugins/Discord2Velocity/config.yaml"))) {
                    config= y.loadAs(in, Map.class);
                    token = (String) config.get("token");
                    channelName = (String) config.get("channel_name");

                    //Login to Discord
                    JDA jda = null;
                    if(Objects.equals(token, "Token of your discord bot.") || token == null || token.equals("")){
                        logger.error("Token not found. Please add your bot token to config.yaml.");
                        return;
                    }
                    else{
                        jda = JDABuilder.createDefault(token)
                                .setRawEventsEnabled(true)
                                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                                .addEventListeners(this)
                                .setActivity(Activity.playing("Minecraft"))
                                .build();
                    }

                    //Wait until JDA is ready
                    try {
                        jda.awaitReady();
                    } catch (InterruptedException e) {
                        logger.error("Failed to start JDA. Please check if the token is correct.");
                        return;
                    }

                    try {
                        textChannel = jda.getTextChannelsByName(channelName, true).get(0);
                    }
                    catch (Exception e){
                        logger.error("Failed to find channel. Please check if the channel name is correct.");
                        return;
                    }
                }
                catch (Exception e){
                    logger.error("§cFailed to load config.yaml. Please check if the file is corrupted.");
                }
                // Output result
                source.sendMessage(Component.text("§aVelocity2Plugin Config reloaded successfully!"));
            } else {
                source.sendMessage(Component.text("§cYou do not have permission to use this command."));
            }
            // Set event as cancelled
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }

    public void broadcastToAllServers(String message) {
        Collection<RegisteredServer> allServers = this.server.getAllServers();

        for (RegisteredServer s: allServers) {
            s.sendMessage(Component.text(message));
        }
    }
}
