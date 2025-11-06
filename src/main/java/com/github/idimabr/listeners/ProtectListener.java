package com.github.idimabr.listeners;

import com.github.idimabr.FinanceObjects;
import com.github.idimabr.api.GameShiftAPI;
import com.github.idimabr.controllers.ItemController;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import lombok.AllArgsConstructor;
import net.Indyuce.mmoitems.api.event.ItemBuildEvent;
import net.Indyuce.mmoitems.api.event.item.IdentifyItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.idimabr.FinanceObjects.debug;

@AllArgsConstructor
public class ProtectListener implements Listener {

    private final FinanceObjects plugin;
    private static final String GAMESHIFT_ASSET_ID = "GAMESHIFT_ASSET_ID";

    /**
     * Impede que itens registrados sejam droppados na morte
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        List<ItemStack> savedItems = new ArrayList<>();

        drops.removeIf(item -> {
            if (isRegistered(item)) {
                savedItems.add(item);
                return true;
            }
            return false;
        });

        event.getEntity().setMetadata("death_soulbound", new FixedMetadataValue(
                plugin, savedItems
        ));
    }

    /**
     * Restaura os itens registrados no inventário do jogador ao respawn
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!player.hasMetadata("death_soulbound")) return;

        @SuppressWarnings("unchecked")
        List<ItemStack> savedItems = (List<ItemStack>) player.getMetadata("death_soulbound").get(0).value();
        if(savedItems == null) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (ItemStack item : savedItems)
                player.getInventory().addItem(item);
            player.removeMetadata("death_soulbound", plugin);
            debug("Restored GameShift items to " + player.getName());
        }, 1);
    }

    /**
     * Impede que itens registrados sejam dropados.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isRegistered(item)) {
            event.setCancelled(true);
            debug("Prevented drop for item: " + item.getType());
        }
    }

    /**
     * PROTEÇÃO COMPLETA - Impede que itens registrados saiam do inventário do jogador
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getHotbarButton() >= 0 ?
                player.getInventory().getItem(event.getHotbarButton()) : null;

        boolean currentRegistered = isRegistered(current);
        boolean cursorRegistered = isRegistered(cursor);
        boolean hotbarRegistered = isRegistered(hotbar);

        // Se nenhum item registrado está envolvido, permite
        if (!currentRegistered && !cursorRegistered && !hotbarRegistered) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();
        Inventory playerInventory = player.getInventory();

        // Se o inventário clicado não é o do jogador, bloqueia TUDO
        if (clickedInventory != null && clickedInventory != playerInventory) {
            if (currentRegistered || cursorRegistered || hotbarRegistered) {
                event.setCancelled(true);
                debug("Prevented moving GameShift item to non-player inventory (Click in other inv)");
                return;
            }
        }

        // Se clicou no inventário do jogador, mas o top inventory não é o do jogador
        // (ou seja, tem um baú/fornalha/etc aberto)
        if (topInventory != null && topInventory != playerInventory && topInventory.getType() != InventoryType.CRAFTING) {

            // Bloqueia SHIFT+CLICK de item registrado
            if (event.isShiftClick() && currentRegistered) {
                event.setCancelled(true);
                debug("Prevented SHIFT+CLICK of GameShift item");
                return;
            }

            // Bloqueia Number Keys (hotbar swap) com item registrado
            if (event.getHotbarButton() >= 0 && (currentRegistered || hotbarRegistered)) {
                event.setCancelled(true);
                debug("Prevented NUMBER KEY swap of GameShift item");
                return;
            }

            // Bloqueia DOUBLE CLICK com item registrado no cursor
            if (event.getClick() == ClickType.DOUBLE_CLICK && cursorRegistered) {
                event.setCancelled(true);
                debug("Prevented DOUBLE CLICK with GameShift item");
                return;
            }

            // Bloqueia pegar item registrado do inventário do jogador quando outro inventário está aberto
            if (clickedInventory == playerInventory && currentRegistered && cursorRegistered == false) {
                // Se está tentando pegar o item (cursor vazio ou diferente)
                if (cursor == null || cursor.getType().isAir() || !cursor.isSimilar(current)) {
                    event.setCancelled(true);
                    debug("Prevented picking up GameShift item with other inventory open");
                    return;
                }
            }

            // Bloqueia colocar item registrado do cursor no inventário do jogador
            // quando outro inventário está aberto (previne exploits)
            if (clickedInventory == playerInventory && cursorRegistered) {
                event.setCancelled(true);
                debug("Prevented placing GameShift item from cursor with other inventory open");
                return;
            }
        }
    }

    /**
     * Impede drag de itens registrados quando outro inventário está aberto
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack draggedItem = event.getOldCursor();
        if (!isRegistered(draggedItem)) return;

        Inventory topInventory = event.getView().getTopInventory();
        Inventory playerInventory = player.getInventory();

        // Se tem outro inventário aberto (não é só o inventário do jogador)
        if (topInventory != null && topInventory != playerInventory &&
                topInventory.getType() != InventoryType.CRAFTING) {

            // Bloqueia qualquer drag de item registrado
            event.setCancelled(true);
            debug("Prevented dragging GameShift item with other inventory open");
            return;
        }

        // Verifica se algum slot do drag é de outro inventário
        for (int slot : event.getRawSlots()) {
            if (slot < topInventory.getSize() && topInventory != playerInventory) {
                event.setCancelled(true);
                debug("Prevented dragging GameShift item to non-player inventory slot");
                return;
            }
        }
    }

    /**
     * Bloqueia swap de mãos (offhand/mainhand) de itens registrados
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isRegistered(event.getMainHandItem()) || isRegistered(event.getOffHandItem())) {
            event.setCancelled(true);
            debug("Prevented swap hands for GameShift item");
        }
    }

    /**
     * Bloqueia transferências automáticas (Hoppers, Shulker, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isRegistered(event.getItem())) {
            event.setCancelled(true);
            debug("Prevented automatic move for GameShift item: " + event.getItem().getType());
        }
    }

    private boolean isRegistered(ItemStack item) {
        if (!isValidItem(item)) return false;
        NBTItem nbt = NBTItem.get(item);
        return isAlreadyRegistered(nbt);
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
}