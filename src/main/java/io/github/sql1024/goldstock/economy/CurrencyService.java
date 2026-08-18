package io.github.sql1024.goldstock.economy;

import java.util.ArrayList;
import java.util.List;

import io.github.sql1024.goldstock.GoldStockPlugin;
import io.github.sql1024.goldstock.util.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The currency is a physical item in the player's inventory — there is no balance anywhere.
 * Only the main storage slots count (hotbar + backpack), not armour or off-hand.
 */
public final class CurrencyService {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final GoldStockPlugin plugin;

    public CurrencyService(GoldStockPlugin plugin) {
        this.plugin = plugin;
    }

    private CurrencySettings settings() {
        return plugin.currency();
    }

    /** How many spendable currency items the player is carrying. */
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
     * Removes exactly {@code amount} items. Returns {@code false} and changes nothing when the
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
     * Gives {@code amount} currency items, dropping whatever does not fit at the player's feet.
     *
     * @return true when everything fitted in the inventory
     */
    public boolean give(Player player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        int maxStack = settings().material().getMaxStackSize();
        List<ItemStack> stacks = new ArrayList<>();
        long remaining = amount;
        while (remaining > 0L) {
            int size = (int) Math.min(remaining, maxStack);
            stacks.add(makeStack(size));
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

    /** Builds a payout stack that the matcher below will accept again. */
    public ItemStack makeStack(int amount) {
        CurrencySettings currency = settings();
        ItemStack item = new ItemStack(currency.material(), Math.max(1, amount));
        switch (currency.match()) {
            case NAME -> item.editMeta(meta -> meta.displayName(Lang.item(currency.matchName())));
            case ITEM_MODEL -> item.editMeta(meta -> {
                meta.setItemModel(currency.itemModel());
                meta.displayName(Lang.item(currency.displayName()));
            });
            case VANILLA, ANY -> {
                // A plain stack is exactly what these modes expect.
            }
        }
        return item;
    }

    /** Whether an item stack counts as money. */
    public boolean isCurrency(ItemStack item) {
        CurrencySettings currency = settings();
        if (item == null || item.getType() != currency.material() || item.getAmount() <= 0) {
            return false;
        }
        return switch (currency.match()) {
            case ANY -> true;
            case VANILLA -> isPlain(item);
            case NAME -> currency.matchName().equals(plainName(item));
            case ITEM_MODEL -> {
                ItemMeta meta = item.getItemMeta();
                yield meta != null && meta.hasItemModel()
                        && currency.itemModel().equals(meta.getItemModel());
            }
        };
    }

    /**
     * A plain item carries no custom name, lore, enchantment or custom model data, so special
     * items are never eaten as change.
     */
    private static boolean isPlain(ItemStack item) {
        if (!item.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        return !meta.hasDisplayName()
                && !meta.hasLore()
                && !meta.hasCustomModelDataComponent()
                && !meta.hasItemModel()
                && meta.getEnchants().isEmpty();
    }

    /** The item's display name as plain text, or {@code null} when it has none. */
    private static String plainName(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        Component name = meta.displayName();
        return name == null ? null : PLAIN.serialize(name);
    }
}
