package com.github.idimabr.commands;

import com.github.idimabr.FinanceObjects;
import com.github.idimabr.api.ItemSynchronizer;
import com.github.idimabr.utils.ConfigUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SyncCommand implements CommandExecutor {

    private final FinanceObjects plugin;

    public SyncCommand(FinanceObjects plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigUtil messages = plugin.getConfig();
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.getString("sync.only-players"));
            return true;
        }

        Player player = (Player) sender;
        player.sendMessage(messages.getString("sync.start"));

        ItemSynchronizer.syncPlayerItems(player, plugin).thenAccept(result -> {
            if (result.isSuccess()) {
                player.sendMessage(messages.getString("sync.success"));
                if (result.getItemsRestored() > 0) {
                    player.sendMessage(messages.getString("sync.success-with-items.header"));
                    for (String itemName : result.getItemNames()) {
                        player.sendMessage(messages.getString("sync.success-with-items.item-format")
                                .replace("{item}", itemName));
                    }
                }
            } else {
                player.sendMessage(messages.getString("sync.failed"));
                if (!result.getItemNames().isEmpty()) {
                    player.sendMessage(messages.getString("sync.failed-with-items.header"));
                    for (String itemName : result.getItemNames()) {
                        player.sendMessage(messages.getString("sync.failed-with-items.item-format")
                                .replace("{item}", itemName));
                    }
                    player.sendMessage(messages.getString("sync.failed-with-items.hint"));
                }
            }
        }).exceptionally(ex -> {
            player.sendMessage(messages.getString("sync.error").replace("{error}", ex.getMessage()));
            ex.printStackTrace();
            return null;
        });

        return true;
    }
}
