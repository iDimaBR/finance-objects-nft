package com.github.idimabr.listeners;

import com.github.idimabr.api.GameShiftAPI;
import com.github.idimabr.controllers.ItemController;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import lombok.AllArgsConstructor;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.ConfigFile;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.ItemBuildEvent;
import net.Indyuce.mmoitems.api.event.item.IdentifyItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.github.idimabr.FinanceObjects.debug;

@AllArgsConstructor
public class RegisterListener implements Listener {

    private static final String GAMESHIFT_ASSET_ID = "GAMESHIFT_ASSET_ID";
    private static final String GAMESHIFT_REGISTERED_AT = "GAMESHIFT_REGISTERED_AT";
    private static final String GAMESHIFT_REGISTERED_BY = "GAMESHIFT_REGISTERED_BY";
    private static final String MMOITEMS_ITEM_ID = "MMOITEMS_ITEM_ID";
    private static final String MMOITEMS_ITEM_TYPE = "MMOITEMS_ITEM_TYPE";
    private static final String SCROLL_OF_IDENTIFICATION = "SCROLL_OF_IDENTIFICATION";
    private static final long IDENTIFICATION_WINDOW_MS = 8500L;
    private static final long INVENTORY_UPDATE_DELAY_TICKS = 1L;

    private final ItemController controller;
    private final Plugin plugin;
    private final Map<UUID, IdentificationContext> identifyingPlayers = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        Player player = event.getPlayer();
        if (!message.startsWith("/mi item identify")) {
            return;
        }

        debug("Command detected: " + message + " from " + player.getName());
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isValidItem(item)) {
            debug("No valid item in main hand");
            return;
        }

        NBTItem nbt = NBTItem.get(item);
        IdentificationContext context = new IdentificationContext(
                player,
                nbt.getString(MMOITEMS_ITEM_TYPE),
                nbt.getString(MMOITEMS_ITEM_ID),
                System.currentTimeMillis()
        );

        identifyingPlayers.put(player.getUniqueId(), context);
        debug("Pre-registered identification context for " + player.getName() +
                " - Type: " + context.unidentifiedType + ", ID: " + context.unidentifiedId);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isValidItem(item)) return;

        NBTItem nbt = NBTItem.get(item);
        String assetId = nbt.getString(GAMESHIFT_ASSET_ID);

        debug("PlayerInteractEvent: " + player.getName() + " interacted with item " + item.getType());
        if (isNullOrEmpty(assetId)) {
            debug("Item has no GameShift ID");
            return;
        }

        debug("This item is registered with GameShift. Asset ID: " + assetId);
    }

    /**
     * Captura identificações feitas com scroll (clique direito)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIdentify(IdentifyItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        NBTItem unidentifiedNBT = event.getUnidentifiedItem();

        IdentificationContext context = new IdentificationContext(
                player,
                unidentifiedNBT.getString(MMOITEMS_ITEM_TYPE),
                unidentifiedNBT.getString(MMOITEMS_ITEM_ID),
                System.currentTimeMillis()
        );

        identifyingPlayers.put(player.getUniqueId(), context);
        debug("IdentifyItemEvent: " + player.getName() + " identified " +
                context.unidentifiedType + ":" + context.unidentifiedId);
    }

    /**
     * Captura a criação do item identificado
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBuild(ItemBuildEvent event) {
        ItemStack item = event.getItemStack();
        if (!isValidItem(item)) return;

        NBTItem nbt = NBTItem.get(item);
        String itemId = nbt.getString(MMOITEMS_ITEM_ID);

        debug("ItemBuildEvent triggered for itemId=" + itemId);

        if (SCROLL_OF_IDENTIFICATION.equals(itemId) || isNullOrEmpty(itemId)) {
            debug("Ignoring scroll or null item");
            return;
        }

        if (isAlreadyRegistered(nbt)) {
            debug("Item already registered in GameShift");
            return;
        }

        Player player = findPlayerWhoIdentified();
        if (player == null) {
            debug("No player matched recent IdentifyItemEvent (window expired or missing)");
            return;
        }

        Map<String, String> nbtAttributes = extractNBTAttributes(nbt);
        debug("Registering item for " + player.getName() + " with " + nbtAttributes.size() + " NBT tags");
        registerItemWithGameShift(player, itemId, nbt, nbtAttributes, item);
    }

    private boolean isValidItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private boolean isAlreadyRegistered(NBTItem nbt) {
        return nbt.hasTag(GAMESHIFT_ASSET_ID) &&
                !isNullOrEmpty(nbt.getString(GAMESHIFT_ASSET_ID));
    }

    private Player findPlayerWhoIdentified() {
        long now = System.currentTimeMillis();
        Player targetPlayer = null;
        UUID targetUUID = null;

        debug("Searching for identification context. Active contexts: " + identifyingPlayers.size());

        for (Map.Entry<UUID, IdentificationContext> entry : identifyingPlayers.entrySet()) {
            IdentificationContext context = entry.getValue();
            long timeDiff = now - context.timestamp;
            debug("Checking context for " + context.player.getName() + " - Age: " + timeDiff + "ms");

            if (isWithinIdentificationWindow(now, context.timestamp)) {
                targetUUID = entry.getKey();
                targetPlayer = Bukkit.getPlayer(targetUUID);
                debug("✓ Found valid context within window");
                break;
            } else {
                debug("✗ Context expired (>" + IDENTIFICATION_WINDOW_MS + "ms)");
            }
        }

        if (targetUUID != null) identifyingPlayers.remove(targetUUID);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            debug("Player not found or offline");
            return null;
        }

        debug("Matched Identify context for player: " + targetPlayer.getName());
        return targetPlayer;
    }

    private boolean isWithinIdentificationWindow(long now, long timestamp) {
        return (now - timestamp) < IDENTIFICATION_WINDOW_MS;
    }

    private Map<String, String> extractNBTAttributes(NBTItem nbt) {
        Map<String, String> attributes = new HashMap<>();
        for (String key : nbt.getTags()) {
            try {
                Object value = nbt.get(key);
                attributes.put(key, value != null ? value.toString() : "null");
            } catch (Exception ex) {
                Bukkit.getLogger().warning("Failed to read NBT key '" + key + "': " + ex.getMessage());
            }
        }
        return attributes;
    }

    private void registerItemWithGameShift(Player player, String itemId, NBTItem nbt, Map<String, String> nbtAttributes, ItemStack item) {
        debug("Calling GameShiftAPI.createItem for " + player.getName());

        String imageUrl = null;
        final Type NBTType = MMOItems.getType(nbt);
        if(NBTType != null) {
            final ConfigFile configFile = NBTType.getConfigFile();
            final ConfigurationSection section = configFile.getConfig().getConfigurationSection(itemId + ".base");
            if(section != null)
                imageUrl = section.getString("custom-model-link");
        }

        controller.registerItem(player, player.getLocation(), nbtAttributes, imageUrl, item)
                .thenAccept(gameshiftId -> {
                    debug("Received GameShift ID: " + gameshiftId);
                    handleRegistrationResult(player, itemId, gameshiftId);
                })
                .exceptionally(ex -> {
                    handleRegistrationError(player, ex);
                    return null;
                });
    }

    private void handleRegistrationResult(Player player, String itemId, String gameshiftId) {
        debug("Handling registration result for " + player.getName() + " item=" + itemId);
        if (isNullOrEmpty(gameshiftId)) {
            sendRegistrationFailureMessage(player);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean success = addGameShiftTagsToItem(player, itemId, gameshiftId);
            debug(success
                    ? "Successfully added GameShift tags to item: " + itemId
                    : "Failed to find item to add GameShift tags: " + itemId);
        }, INVENTORY_UPDATE_DELAY_TICKS);
    }

    private boolean addGameShiftTagsToItem(Player player, String mmoItemId, String gameshiftId) {
        if (tryAddTagsToCursor(player, mmoItemId, gameshiftId)) return true;
        return tryAddTagsToInventory(player, mmoItemId, gameshiftId);
    }

    private boolean tryAddTagsToCursor(Player player, String mmoItemId, String gameshiftId) {
        ItemStack cursor = player.getItemOnCursor();
        if (!isValidItem(cursor)) return false;

        NBTItem nbtCursor = NBTItem.get(cursor);
        if (!mmoItemId.equals(nbtCursor.getString(MMOITEMS_ITEM_ID))) return false;
        if (isAlreadyRegistered(nbtCursor)) return false;

        debug("Adding GameShift tags to cursor item for " + player.getName());
        ItemStack modifiedItem = addGameShiftTags(nbtCursor, gameshiftId, player.getName());
        player.setItemOnCursor(modifiedItem);
        return true;
    }

    private boolean tryAddTagsToInventory(Player player, String mmoItemId, String gameshiftId) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isValidItem(item)) continue;

            NBTItem nbtItem = NBTItem.get(item);
            if (!mmoItemId.equals(nbtItem.getString(MMOITEMS_ITEM_ID))) continue;
            if (isAlreadyRegistered(nbtItem)) continue;

            debug("Adding GameShift tags to inventory slot " + slot + " for " + player.getName());
            ItemStack modifiedItem = addGameShiftTags(nbtItem, gameshiftId, player.getName());
            player.getInventory().setItem(slot, modifiedItem);
            return true;
        }
        return false;
    }

    private ItemStack addGameShiftTags(NBTItem nbtItem, String gameshiftId, String playerName) {
        nbtItem.addTag(
                new ItemTag(GAMESHIFT_ASSET_ID, gameshiftId),
                new ItemTag(GAMESHIFT_REGISTERED_AT, System.currentTimeMillis()),
                new ItemTag(GAMESHIFT_REGISTERED_BY, playerName)
        );
        final ItemStack item = nbtItem.toItem();
        final ItemMeta meta = item.getItemMeta();
        if(meta != null && meta.hasDisplayName()) {
            meta.setDisplayName(meta.getDisplayName() + GameShiftAPI.ICON_CONFIRMED);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void sendRegistrationFailureMessage(Player player) {
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.getLogger().warning("Failed to register item for " + player.getName()));
    }

    private void handleRegistrationError(Player player, Throwable ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getLogger().severe("Error registering item for " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        });
    }

    private record IdentificationContext(Player player, String unidentifiedType, String unidentifiedId, long timestamp) {}
}