package io.github.sql1024.goldstock.economy;

import java.util.ArrayList;
import java.util.List;

import io.github.sql1024.goldstock.GoldStockPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The currency is the physical gold ingot in the player's inventory — there is no balance
 * anywhere. Only the main storage slots count (hotbar + backpack), not armour or off-hand.
 */
public final class GoldEconomy {

    public static final Material CURRENCY = Material.GOLD_INGOT;

    private final GoldStockPlugin plugin;

    public GoldEconomy(GoldStockPlugin plugin) {
        this.plugin = plugin;
    }

    /** How many spendable gold ingots the player is carrying. */
    public int count(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isCurrency(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Removes exactly {@code amount} ingots. Returns {@code false} and changes nothing when the
     * player does not carry enough.
     */
    public boolean take(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (count(player) < amount) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isCurrency(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            if (take >= item.getAmount()) {
                contents[slot] = null;
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
        if (remaining > 0) {
            // Should be unreachable because of the count() check above.
            return false;
        }
        inventory.setStorageContents(contents);
        return true;
    }

    /**
     * Gives {@code amount} ingots, dropping whatever does not fit at the player's feet.
     *
     * @return true when everything fitted in the inventory
     */
    public boolean give(Player player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        int maxStack = CURRENCY.getMaxStackSize();
        List<ItemStack> stacks = new ArrayList<>();
        long remaining = amount;
        while (remaining > 0L) {
            int size = (int) Math.min(remaining, maxStack);
            stacks.add(new ItemStack(CURRENCY, size));
            remaining -= size;
        }

        var leftover = player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
        if (leftover.isEmpty()) {
            return true;
        }
        for (ItemStack item : leftover.values()) {
            Item dropped = player.getWorld().dropItem(player.getLocation(), item);
            dropped.setOwner(player.getUniqueId());
        }
        return false;
    }

    /**
     * Whether an item counts as currency. With {@code currency.strict-items} enabled, ingots
     * carrying a custom name, lore or enchantment are left alone so special items are not eaten.
     */
    public boolean isCurrency(ItemStack item) {
        if (item == null || item.getType() != CURRENCY || item.getAmount() <= 0) {
            return false;
        }
        if (!plugin.settings().strictCurrencyItems() || !item.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        return !meta.hasDisplayName()
                && !meta.hasLore()
                && !meta.hasCustomModelDataComponent()
                && meta.getEnchants().isEmpty();
    }
}
