package io.github.sql1024.goldstock.trade;

import io.github.sql1024.goldstock.GoldStockPlugin;
import io.github.sql1024.goldstock.market.MarketSettings;
import io.github.sql1024.goldstock.market.Stock;
import io.github.sql1024.goldstock.portfolio.Holding;
import io.github.sql1024.goldstock.util.Fmt;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Validates and executes trades. Every method runs on the main thread, sends its own feedback,
 * and returns whether the trade went through.
 */
public final class TradeService {

    private final GoldStockPlugin plugin;

    public TradeService(GoldStockPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean buy(Player player, Stock stock, int shares) {
        MarketSettings settings = plugin.settings();
        var lang = plugin.lang();

        if (shares <= 0) {
            lang.send(player, "invalid-number", "input", shares);
            return fail(player);
        }
        if (shares > settings.maxSharesPerTrade()) {
            lang.send(player, "shares-too-many", "max", settings.maxSharesPerTrade());
            return fail(player);
        }

        Holding holding = plugin.portfolios().get(player.getUniqueId(), stock.symbol());
        if (holding.shares() + shares > settings.maxSharesPerStock()) {
            lang.send(player, "hold-limit",
                    "max", settings.maxSharesPerStock(),
                    "symbol", stock.symbol(),
                    "held", holding.shares());
            return fail(player);
        }

        double unitPrice = stock.price();
        long cost = settings.buyCost(unitPrice, shares);
        if (cost > settings.maxGoldPerTransaction()) {
            lang.send(player, "gold-limit",
                    "max", Fmt.gold(settings.maxGoldPerTransaction()),
                    "gold", Fmt.gold(cost));
            return fail(player);
        }

        int carried = plugin.economy().count(player);
        if (carried < cost) {
            lang.send(player, "not-enough-gold", "need", Fmt.gold(cost), "have", Fmt.gold(carried));
            return fail(player);
        }
        if (!plugin.economy().take(player, (int) cost)) {
            lang.send(player, "not-enough-gold", "need", Fmt.gold(cost), "have", Fmt.gold(carried));
            return fail(player);
        }

        plugin.portfolios().set(player.getUniqueId(), stock.symbol(), holding.plus(shares, cost));
        plugin.database().logTransaction(
                player.getUniqueId(), player.getName(), stock.symbol(), "BUY", shares, unitPrice, cost);

        lang.send(player, "buy-success",
                "shares", shares,
                "name", stock.displayName(),
                "symbol", stock.symbol(),
                "price", Fmt.price(unitPrice),
                "gold", Fmt.gold(cost),
                "fee", Fmt.gold(settings.buyFee(unitPrice, shares)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        return true;
    }

    public boolean sell(Player player, Stock stock, int shares) {
        MarketSettings settings = plugin.settings();
        var lang = plugin.lang();

        Holding holding = plugin.portfolios().get(player.getUniqueId(), stock.symbol());
        if (shares <= 0) {
            lang.send(player, "invalid-number", "input", shares);
            return fail(player);
        }
        if (holding.shares() <= 0 || holding.shares() < shares) {
            lang.send(player, "not-enough-shares", "held", holding.shares(), "symbol", stock.symbol());
            return fail(player);
        }
        if (shares > settings.maxSharesPerTrade()) {
            lang.send(player, "shares-too-many", "max", settings.maxSharesPerTrade());
            return fail(player);
        }

        double unitPrice = stock.price();
        long proceeds = settings.sellProceeds(unitPrice, shares);
        if (proceeds > settings.maxGoldPerTransaction()) {
            lang.send(player, "gold-limit",
                    "max", Fmt.gold(settings.maxGoldPerTransaction()),
                    "gold", Fmt.gold(proceeds));
            return fail(player);
        }

        long basis = holding.basisFor(shares);
        plugin.portfolios().set(player.getUniqueId(), stock.symbol(), holding.minus(shares));

        boolean fitted = plugin.economy().give(player, proceeds);
        plugin.database().logTransaction(
                player.getUniqueId(), player.getName(), stock.symbol(), "SELL", shares, unitPrice, proceeds);

        lang.send(player, "sell-success",
                "shares", shares,
                "name", stock.displayName(),
                "symbol", stock.symbol(),
                "price", Fmt.price(unitPrice),
                "gold", Fmt.gold(proceeds),
                "fee", Fmt.gold(settings.sellFee(unitPrice, shares)),
                "pnl", Fmt.pnlTag(proceeds - basis));
        if (!fitted) {
            lang.send(player, "inventory-full");
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
        return true;
    }

    /** Sells every share the player holds of one stock. */
    public boolean sellAll(Player player, Stock stock) {
        int held = plugin.portfolios().shares(player.getUniqueId(), stock.symbol());
        if (held <= 0) {
            plugin.lang().send(player, "not-enough-shares", "held", 0, "symbol", stock.symbol());
            return fail(player);
        }
        return sell(player, stock, Math.min(held, plugin.settings().maxSharesPerTrade()));
    }

    private boolean fail(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        return false;
    }
}
