package io.github.sql1024.goldstock.market;

import org.bukkit.configuration.file.FileConfiguration;

/** Snapshot of the non-stock parts of config.yml, re-read on every reload. */
public record MarketSettings(
        long updateIntervalSeconds,
        double maxTickChangePercent,
        int historyPoints,
        double feePercent,
        boolean broadcastUpdates,
        double announceBigMovesPercent,
        int maxSharesPerTrade,
        int maxSharesPerStock,
        long maxGoldPerTransaction,
        boolean strictCurrencyItems,
        String guiTitle,
        int chartWidth) {

    public static MarketSettings from(FileConfiguration config) {
        return new MarketSettings(
                Math.max(5L, config.getLong("market.update-interval-seconds", 300L)),
                Math.clamp(config.getDouble("market.max-tick-change-percent", 12.0), 0.1, 100.0),
                Math.clamp(config.getInt("market.history-points", 120), 2, 4096),
                Math.clamp(config.getDouble("market.fee-percent", 1.0), 0.0, 90.0),
                config.getBoolean("market.broadcast-updates", false),
                Math.max(0.0, config.getDouble("market.announce-big-moves-percent", 10.0)),
                Math.max(1, config.getInt("limits.max-shares-per-trade", 5000)),
                Math.max(1, config.getInt("limits.max-shares-per-stock", 20000)),
                Math.max(1L, config.getLong("limits.max-gold-per-transaction", 100000L)),
                config.getBoolean("currency.strict-items", true),
                config.getString("gui.title", "<gold>金錠股市"),
                Math.clamp(config.getInt("gui.chart-width", 24), 4, 64));
    }

    public long updateIntervalTicks() {
        return updateIntervalSeconds * 20L;
    }

    /** Gold owed when buying {@code shares} at {@code unitPrice}, fee included, rounded up. */
    public long buyCost(double unitPrice, int shares) {
        double gross = unitPrice * shares;
        return (long) Math.ceil(gross * (1.0 + feePercent / 100.0));
    }

    /** Gold paid out when selling {@code shares} at {@code unitPrice}, fee deducted, rounded down. */
    public long sellProceeds(double unitPrice, int shares) {
        double gross = unitPrice * shares;
        return (long) Math.floor(gross * (1.0 - feePercent / 100.0));
    }

    /** The fee part of a buy, in gold. */
    public long buyFee(double unitPrice, int shares) {
        return buyCost(unitPrice, shares) - (long) Math.ceil(unitPrice * shares);
    }

    /** The fee part of a sell, in gold. */
    public long sellFee(double unitPrice, int shares) {
        return (long) Math.floor(unitPrice * shares) - sellProceeds(unitPrice, shares);
    }
}
