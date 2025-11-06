package com.github.idimabr.controllers;

import com.github.idimabr.FinanceObjects;
import com.github.idimabr.api.GameShiftAPI;
import lombok.AllArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static com.github.idimabr.FinanceObjects.debug;

@AllArgsConstructor
public class ItemController {

    private final FinanceObjects plugin;

    public CompletableFuture<String> registerItem(Player player, Location location, Map<String, String> nbtAttributes, String imageUrl, ItemStack item) {
        return GameShiftAPI.createItem(
                    player.getUniqueId(),
                    player.getName(),
                    location,
                    System.currentTimeMillis(),
                    nbtAttributes,
                    imageUrl,
                    item
                ).thenApply(json -> json.getString("id"))
                .exceptionally(ex -> {
                    debug("Error register item: " + ex.getMessage());
                    ex.printStackTrace();
                    return null;
                });
    }
}
