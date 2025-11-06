package com.github.idimabr;

import com.github.idimabr.api.GameShiftAPI;
import com.github.idimabr.commands.SyncCommand;
import com.github.idimabr.controllers.ItemController;
import com.github.idimabr.listeners.ProtectListener;
import com.github.idimabr.listeners.RegisterListener;
import com.github.idimabr.utils.ConfigUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class FinanceObjects extends JavaPlugin {

    private ConfigUtil config;
    private ItemController controller;

    @Override
    public void onLoad() {
        this.config = new ConfigUtil(this, "config.yml");
    }

    @Override
    public void onEnable() {
        boolean debug = config.getBoolean("debug", true);
        String apiKey = config.getString("api-key");
        String collectionId = config.getString("collection-id");
        String imageUrl = config.getString("default-image-url");
        String iconConfirmed = config.getString("icon-confirmed");

        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            getLogger().severe("=================================================");
            getLogger().severe("GAMESHIFT API KEY NOT CONFIGURED!");
            getLogger().severe("Please set 'api-key' in config.yml");
            getLogger().severe("=================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (collectionId == null || collectionId.isEmpty()) {
            getLogger().severe("=================================================");
            getLogger().severe("GAMESHIFT COLLECTION ID NOT CONFIGURED!");
            getLogger().severe("Please set 'collection-id' in config.yml");
            getLogger().severe("=================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GameShiftAPI.load(debug, apiKey, collectionId, imageUrl, iconConfirmed);
        getLogger().info("GameShift API initialized successfully!");
        getLogger().info("Collection ID: " + collectionId);

        this.controller = new ItemController(this);
        Bukkit.getPluginManager().registerEvents(new RegisterListener(controller, this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectListener(this), this);
        getCommand("sync").setExecutor(new SyncCommand(this));
        getLogger().info("FinanceObjects enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("FinanceObjects disabled!");
    }

    public static void debug(String msg) {
        if (GameShiftAPI.DEBUG) {
            Bukkit.getLogger().info("[GameShift-Debug] " + msg);
        }
    }
}
