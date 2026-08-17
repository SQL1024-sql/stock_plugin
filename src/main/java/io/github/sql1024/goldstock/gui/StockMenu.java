package io.github.sql1024.goldstock.gui;

import java.util.HashMap;
import java.util.Map;

import io.github.sql1024.goldstock.GoldStockPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Base class for the plugin's chest menus. */
public abstract class StockMenu implements InventoryHolder {

    /** The 21 slots used for stock entries in a 6-row chest. */
    protected static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
    };

    protected static final int SLOT_PREVIOUS = 45;
    protected static final int SLOT_HELP = 48;
    protected static final int SLOT_SWITCH = 49;
    protected static final int SLOT_REFRESH = 50;
    protected static final int SLOT_NEXT = 53;

    protected final GoldStockPlugin plugin;
    protected final Player viewer;
    protected final Inventory inventory;
    /** Slot index to stock symbol, rebuilt on every render. */
    protected final Map<Integer, String> slotSymbols = new HashMap<>();

    protected int page;

    protected StockMenu(GoldStockPlugin plugin, Player viewer, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    public final Player viewer() {
        return viewer;
    }

    /** Redraws every slot from the current market state. */
    public abstract void render();

    /** Handles a click on one of the menu's own slots. */
    public abstract void onClick(int slot, ClickType click);

    protected final int pageCount(int entries) {
        return Math.max(1, (int) Math.ceil((double) entries / CONTENT_SLOTS.length));
    }

    protected final void clampPage(int entries) {
        page = Math.clamp(page, 0, pageCount(entries) - 1);
    }

    protected final void drawFrame(int entries, String switchNameMini, java.util.List<String> switchLore) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, Icons.filler());
        }
        for (int slot : CONTENT_SLOTS) {
            inventory.setItem(slot, null);
        }

        int pages = pageCount(entries);
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS, Icons.of(org.bukkit.Material.ARROW,
                    "<yellow>← 上一頁",
                    java.util.List.of("<gray>第 " + page + " / " + pages + " 頁")));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, Icons.of(org.bukkit.Material.ARROW,
                    "<yellow>下一頁 →",
                    java.util.List.of("<gray>第 " + (page + 2) + " / " + pages + " 頁")));
        }

        inventory.setItem(SLOT_REFRESH, Icons.of(org.bukkit.Material.CLOCK,
                "<aqua>重新整理",
                java.util.List.of(
                        "<gray>股價每 <white>" + plugin.settings().updateIntervalSeconds()
                                + "</white> 秒自動更新一次",
                        "<gray>手續費 <white>" + plugin.settings().feePercent() + "%</white>")));

        inventory.setItem(SLOT_HELP, Icons.of(org.bukkit.Material.BOOK,
                "<gold>怎麼玩",
                java.util.List.of(
                        "<gray>貨幣是背包裡的 <gold>金錠</gold><gray>。",
                        "<yellow>左鍵 <gray>買 1 股　<yellow>Shift+左鍵 <gray>買 10 股",
                        "<yellow>右鍵 <gray>賣 1 股　<yellow>Shift+右鍵 <gray>賣 10 股",
                        "<yellow>中鍵 <gray>在聊天欄看詳細走勢",
                        "<dark_gray>指令：/stock help")));

        inventory.setItem(SLOT_SWITCH, Icons.of(org.bukkit.Material.CHEST, switchNameMini, switchLore));
    }
}
