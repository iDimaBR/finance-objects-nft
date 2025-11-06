package com.github.idimabr.api;

import com.github.idimabr.models.GameShiftItem;
import com.github.idimabr.models.SyncResult;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.github.idimabr.FinanceObjects.debug;

public class ItemSynchronizer {

    private static final String GAMESHIFT_ASSET_ID = "GAMESHIFT_ASSET_ID";
    private static final String GAMESHIFT_REGISTERED_AT = "GAMESHIFT_REGISTERED_AT";
    private static final String GAMESHIFT_REGISTERED_BY = "GAMESHIFT_REGISTERED_BY";
    private static final String MMOITEMS_ITEM_ID = "MMOITEMS_ITEM_ID";
    private static final String MMOITEMS_ITEM_TYPE = "MMOITEMS_ITEM_TYPE";

    public static CompletableFuture<SyncResult> syncPlayerItems(Player player, Plugin plugin) {
        debug("Starting sync for player: " + player.getName());

        return GameShiftAPI.getUserItems(player.getUniqueId()).thenCompose(response -> {
            CompletableFuture<SyncResult> future = new CompletableFuture<>();

            if (response == null) {
                debug("Failed to fetch items from API");
                future.complete(new SyncResult(false, "Failed to fetch items from API", 0, 0, new ArrayList<>()));
                return future;
            }

            try {
                JSONArray items = response.getJSONArray("data");
                debug("Found " + items.length() + " items in GameShift");

                Map<String, GameShiftItem> gameshiftItems = new HashMap<>();

                for (int i = 0; i < items.length(); i++) {
                    JSONObject itemData = items.getJSONObject(i);
                    JSONObject item = itemData.getJSONObject("item");

                    String id = item.getString("id");
                    String name = item.getString("name").replaceAll("^\"|\"$", "");
                    JSONArray attributes = item.getJSONArray("attributes");

                    String itemId = extractAttribute(attributes, "mmoitems:MMOITEMS_ITEM_ID");
                    String itemType = extractAttribute(attributes, "mmoitems:MMOITEMS_ITEM_TYPE");

                    if (itemId != null && itemType != null) {
                        gameshiftItems.put(id, new GameShiftItem(id, name, itemType, itemId, attributes));
                        debug("Mapped item: " + name + " (Type: " + itemType + ", ID: " + itemId + ")");
                    } else {
                        debug("Skipping item " + id + " - missing ITEM_ID or ITEM_TYPE");
                    }
                }

                if (gameshiftItems.isEmpty()) {
                    debug("No valid items found in GameShift");
                    future.complete(new SyncResult(true, "No items found in GameShift", 0, 0, new ArrayList<>()));
                    return future;
                }

                Set<String> inventoryItemIds = getInventoryGameShiftIds(player);
                debug("Player has " + inventoryItemIds.size() + " GameShift items in inventory");

                List<GameShiftItem> missingItems = new ArrayList<>();
                for (GameShiftItem gsItem : gameshiftItems.values()) {
                    if (!inventoryItemIds.contains(gsItem.getGameshiftId())) {
                        missingItems.add(gsItem);
                        debug("Missing item: " + gsItem.getName() + " (ID: " + gsItem.getGameshiftId() + ")");
                    }
                }

                if (missingItems.isEmpty()) {
                    debug("All items are already in inventory");
                    future.complete(new SyncResult(true, "All items are already in your inventory", 0, gameshiftItems.size(), new ArrayList<>()));
                    return future;
                }

                debug("Found " + missingItems.size() + " missing items");
                int availableSlots = getAvailableInventorySlots(player);
                debug("Available inventory slots: " + availableSlots);

                if (availableSlots < missingItems.size()) {
                    List<String> itemNames = new ArrayList<>();
                    for (GameShiftItem item : missingItems) {
                        itemNames.add(item.getName());
                    }
                    debug("Not enough space! Need " + missingItems.size() + " slots, have " + availableSlots);
                    future.complete(new SyncResult(false,
                            String.format("Inventory full! You need %d free slots to receive the items",
                                    missingItems.size()),
                            0, gameshiftItems.size(), itemNames));
                    return future;
                }

                int totalItems = gameshiftItems.size();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int restored = 0;
                    List<String> restoredNames = new ArrayList<>();

                    for (GameShiftItem gsItem : missingItems) {
                        debug("Attempting to recreate item: " + gsItem.getName());
                        ItemStack item = recreateItemFromAttributes(gsItem, player);
                        if (item != null) {
                            player.getInventory().addItem(item);
                            restored++;
                            restoredNames.add(gsItem.getName());
                            debug("✓ Successfully restored: " + gsItem.getName());
                        } else {
                            debug("✗ Failed to recreate: " + gsItem.getName());
                        }
                    }

                    debug("Sync complete! Restored " + restored + "/" + missingItems.size() + " items");
                    future.complete(new SyncResult(true,
                            String.format("Sync complete! %d/%d items restored", restored, missingItems.size()),
                            restored, totalItems, restoredNames));
                });

            } catch (Exception e) {
                debug("Error during sync: " + e.getMessage());
                e.printStackTrace();
                future.complete(new SyncResult(false, "Error processing items: " + e.getMessage(), 0, 0, new ArrayList<>()));
            }

            return future;
        });
    }

    private static String extractAttribute(JSONArray attributes, String traitType) {
        for (int i = 0; i < attributes.length(); i++) {
            JSONObject attr = attributes.getJSONObject(i);
            if (attr.getString("traitType").equals(traitType)) {
                String value = attr.getString("value");
                final String string = value.replaceAll("^\"|\"$", "").replaceAll("^'|'$", "");
                System.out.println("Attribute: " + traitType + " / Value: " + string);
                return string;
            }
        }
        return null;
    }

    private static Set<String> getInventoryGameShiftIds(Player player) {
        Set<String> ids = new HashSet<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (!isValidItem(item)) continue;

            NBTItem nbtItem = NBTItem.get(item);
            if (nbtItem.hasTag(GAMESHIFT_ASSET_ID)) {
                String gameshiftId = nbtItem.getString(GAMESHIFT_ASSET_ID);
                if (gameshiftId != null && !gameshiftId.isEmpty()) {
                    ids.add(gameshiftId);
                }
            }
        }

        return ids;
    }

    private static int getAvailableInventorySlots(Player player) {
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) {
                empty++;
            }
        }
        return empty;
    }

    private static ItemStack recreateItemFromAttributes(GameShiftItem gsItem, Player player) {
        try {
            Type type = MMOItems.plugin.getTypes().get(gsItem.getItemType());
            if (type == null) {
                debug("❌ Invalid MMOItems type: " + gsItem.getItemType());
                return null;
            }

            MMOItem mmoItem = MMOItems.plugin.getMMOItem(type, gsItem.getItemId());
            if (mmoItem == null) {
                debug("MMOItem not found: " + gsItem.getItemType() + "/" + gsItem.getItemId());
                return null;
            }

            ItemStack item = mmoItem.newBuilder().build();
            if (item == null) return null;

            debug("# Starting create gameitem [" + gsItem.getGameshiftId() + "]");
            Map<String, Object> parsedAttributes = new HashMap<>();
            String displayName = null;
            List<String> loreLines = new ArrayList<>();
            String foundBy = null;

            for (int i = 0; i < gsItem.getAttributes().length(); i++) {
                JSONObject attr = gsItem.getAttributes().optJSONObject(i);
                if (attr == null) continue;

                String traitType = attr.optString("traitType");
                String rawValue = attr.optString("value");

                rawValue = rawValue.replaceAll("^['\"]|['\"]$", "");
                if (traitType.equals("mmoitems:MMOITEMS_NAME")) {
                    displayName = rawValue;
                    debug("✓ Found display name: " + displayName);
                    continue;
                }

                if (traitType.equals("mmoitems:MMOITEMS_DYNAMIC_LORE")) {
                    loreLines = parseLoreFromJson(rawValue);
                    debug("✓ Found lore with " + loreLines.size() + " lines");
                    continue;
                }

                if (traitType.equals("minex:found_by")) {
                    foundBy = rawValue;
                    continue;
                }

                if (traitType.equals("mmoitems:MMOITEMS_LORE")) {
                    continue;
                }

                if (traitType.startsWith("mmoitems:")) {
                    String statName = traitType.substring("mmoitems:".length());
                    Object parsedValue = parseValueAutomatically(rawValue);
                    if (parsedValue != null) {
                        parsedAttributes.put(statName, parsedValue);
                        debug("✓ Parsed stat: " + statName + " = " + parsedValue);
                    }
                }
            }

            if (foundBy == null || foundBy.isEmpty()) {
                foundBy = player.getName();
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                debug("❌ Cannot get ItemMeta");
                return null;
            }

            if (displayName != null && !displayName.isEmpty()) {
                meta.setDisplayName(translateColorCodes(displayName) + GameShiftAPI.ICON_CONFIRMED);
                debug("✓ Applied display name");
            }

            if (!loreLines.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : loreLines) {
                    coloredLore.add(translateColorCodes(line));
                }
                meta.setLore(coloredLore);
                debug("✓ Applied lore (" + loreLines.size() + " lines)");
            }

            item.setItemMeta(meta);
            NBTItem nbtItem = NBTItem.get(item);
            List<ItemTag> tags = new ArrayList<>();
            tags.add(new ItemTag(GAMESHIFT_ASSET_ID, gsItem.getGameshiftId()));
            tags.add(new ItemTag(GAMESHIFT_REGISTERED_AT, String.valueOf(System.currentTimeMillis())));
            tags.add(new ItemTag(GAMESHIFT_REGISTERED_BY, foundBy));

            if (!loreLines.isEmpty()) {
                JSONArray loreArray = new JSONArray();
                for (String line : loreLines) {
                    loreArray.put(line);
                }
                tags.add(new ItemTag("MMOITEMS_DYNAMIC_LORE", loreArray.toString()));
            }

            for (Map.Entry<String, Object> entry : parsedAttributes.entrySet()) {
                String statName = entry.getKey();
                Object value = entry.getValue();
                tags.removeIf(tag -> tag.getPath().equalsIgnoreCase(statName));

                tags.add(new ItemTag(statName, value));
                debug("✓ Applied NBT: " + statName + " = " + value + " (" + value.getClass().getSimpleName() + ")");
            }

            nbtItem.addTag(tags);
            ItemStack finalItem = nbtItem.toItem();
            debug("✅ Item recreated successfully: " + gsItem.getGameshiftId());

            return finalItem;

        } catch (Exception e) {
            debug("❌ Error recreating item " + gsItem.getGameshiftId() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static List<String> parseLoreFromJson(String loreJson) {
        List<String> loreLines = new ArrayList<>();

        if (loreJson == null || loreJson.isEmpty()) {
            return loreLines;
        }

        debug("📋 Parsing lore: " + loreJson.substring(0, Math.min(100, loreJson.length())) + "...");
        String cleaned = loreJson.trim();
        if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
            debug("✓ Removed surrounding single quotes");
        }

        try {
            JSONArray loreArray = new JSONArray(cleaned);
            for (int i = 0; i < loreArray.length(); i++) {
                loreLines.add(loreArray.getString(i));
            }
            debug("✓ Parsed as valid JSONArray (" + loreLines.size() + " lines)");
            return loreLines;
        } catch (Exception e) {
            debug("⚠ Not a valid JSONArray, trying sanitization...");
        }

        try {
            String sanitized = cleaned
                    .replace("\\\"", "\"")
                    .replace("\\'", "'");

            JSONArray loreArray = new JSONArray(sanitized);
            for (int i = 0; i < loreArray.length(); i++) {
                loreLines.add(loreArray.getString(i));
            }
            debug("✓ Parsed after sanitization (" + loreLines.size() + " lines)");
            return loreLines;
        } catch (Exception e) {
            debug("⚠ Sanitization failed, using manual split...");
        }

        try {
            String content = cleaned;
            if (content.startsWith("[")) content = content.substring(1);
            if (content.endsWith("]")) content = content.substring(0, content.length() - 1);

            String[] parts = content.split("\",\\s*\"");

            for (String part : parts) {
                String line = part
                        .replaceAll("^\"|\"$", "")
                        .replace("\\\"", "\"")
                        .replace("\\'", "'")
                        .trim();

                if (!line.isEmpty()) {
                    loreLines.add(line);
                }
            }

            debug("✓ Manual split extracted " + loreLines.size() + " lines");
            return loreLines;

        } catch (Exception e) {
            debug("❌ All lore parsing methods failed: " + e.getMessage());
            loreLines.add("§cLore parsing error");
        }

        return loreLines;
    }

    private static Object parseValueAutomatically(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) return null;

        rawValue = rawValue.trim();
        if (rawValue.startsWith("'") && rawValue.endsWith("'") && rawValue.length() > 2) {
            rawValue = rawValue.substring(1, rawValue.length() - 1);
            debug("  → Removed outer single quotes");
        }

        // Boolean
        if (rawValue.equals("1b") || rawValue.equalsIgnoreCase("true")) {
            return true;
        }
        if (rawValue.equals("0b") || rawValue.equalsIgnoreCase("false")) {
            return false;
        }

        // Integer
        try {
            if (rawValue.matches("-?\\d+")) {
                return Integer.parseInt(rawValue);
            }
        } catch (Exception ignored) {}

        // Double
        try {
            String cleanValue = rawValue.toLowerCase().replace("d", "");
            if (rawValue.endsWith("d") || cleanValue.matches("-?\\d+\\.\\d+")) {
                return Double.parseDouble(cleanValue);
            }
        } catch (Exception ignored) {}

        // JSONObject
        if (rawValue.startsWith("{")) {
            try {
                // Sanitizar aspas escapadas antes de parsear
                String sanitized = rawValue.replace("\\\"", "\"");
                return new JSONObject(sanitized);
            } catch (Exception e) {
                debug("  ⚠ Failed to parse JSONObject: " + e.getMessage());
            }
        }

        // JSON ARRAY
        if (rawValue.startsWith("[")) {
            try {
                String sanitized = rawValue.replace("\\\"", "\"");
                return new JSONArray(sanitized);
            } catch (Exception e) {
                debug("  ⚠ Failed to parse JSONArray: " + e.getMessage());
            }
        }

        return rawValue;
    }

    private static String translateColorCodes(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    private static boolean isValidItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }
}