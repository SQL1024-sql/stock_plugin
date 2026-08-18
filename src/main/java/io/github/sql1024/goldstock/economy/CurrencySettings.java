package io.github.sql1024.goldstock.economy;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Describes the item used as money. The currency does not have to be a vanilla item — matching by
 * name or by {@code item_model} lets custom items from other plugins be used as-is.
 *
 * @param material   base material of the currency item
 * @param displayName MiniMessage name shown in messages and on the GUI
 * @param match      how an item stack is recognised as currency
 * @param matchName  plain-text item name required by {@link MatchMode#NAME}
 * @param itemModel  {@code item_model} key required by {@link MatchMode#ITEM_MODEL}
 */
public record CurrencySettings(
        Material material,
        String displayName,
        MatchMode match,
        String matchName,
        NamespacedKey itemModel) {

    public enum MatchMode {
        /** A clean vanilla item: no custom name, lore, enchantment or custom model data. */
        VANILLA,
        /** The item's display name must equal {@code match-name}. */
        NAME,
        /** The item's {@code item_model} must equal {@code match-item-model}. */
        ITEM_MODEL,
        /** Any stack of the right material counts, named or not. */
        ANY
    }

    public static CurrencySettings from(FileConfiguration config, java.util.logging.Logger logger) {
        String materialName = config.getString("currency.material", "GOLD_INGOT");
        Material material = Material.matchMaterial(materialName == null ? "GOLD_INGOT" : materialName);
        if (material == null || !material.isItem()) {
            logger.warning("currency.material『" + materialName + "』不是有效的物品，改用 GOLD_INGOT。");
            material = Material.GOLD_INGOT;
        }

        String displayName = config.getString("currency.display-name", "金錠");
        String matchName = config.getString("currency.match-name", "");
        String modeName = config.getString("currency.match", "vanilla");
        MatchMode mode;
        try {
            mode = MatchMode.valueOf(modeName == null
                    ? "VANILLA"
                    : modeName.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            logger.warning("currency.match『" + modeName + "』無效，改用 vanilla。");
            mode = MatchMode.VANILLA;
        }

        String modelKey = config.getString("currency.match-item-model", "");
        NamespacedKey itemModel = modelKey == null || modelKey.isBlank()
                ? null
                : NamespacedKey.fromString(modelKey.trim());
        if (mode == MatchMode.ITEM_MODEL && itemModel == null) {
            logger.warning("currency.match 設成 item-model，但 match-item-model 不是有效的 key，改用 vanilla。");
            mode = MatchMode.VANILLA;
        }
        if (mode == MatchMode.NAME && (matchName == null || matchName.isBlank())) {
            logger.warning("currency.match 設成 name，但 match-name 是空的，改用 vanilla。");
            mode = MatchMode.VANILLA;
        }

        return new CurrencySettings(material, displayName, mode,
                matchName == null ? "" : matchName, itemModel);
    }
}
