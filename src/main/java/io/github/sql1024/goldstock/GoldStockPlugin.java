package io.github.sql1024.goldstock;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import io.github.sql1024.goldstock.command.StockCommand;
import io.github.sql1024.goldstock.economy.CurrencyService;
import io.github.sql1024.goldstock.economy.CurrencySettings;
import io.github.sql1024.goldstock.gui.MenuListener;
import io.github.sql1024.goldstock.gui.MenuManager;
import io.github.sql1024.goldstock.listener.PlayerListener;
import io.github.sql1024.goldstock.market.IndicatorSettings;
import io.github.sql1024.goldstock.market.MarketManager;
import io.github.sql1024.goldstock.market.MarketSettings;
import io.github.sql1024.goldstock.market.Stock;
import io.github.sql1024.goldstock.news.NewsManager;
import io.github.sql1024.goldstock.news.NewsSettings;
import io.github.sql1024.goldstock.portfolio.PortfolioManager;
import io.github.sql1024.goldstock.storage.Database;
import io.github.sql1024.goldstock.trade.TradeService;
import io.github.sql1024.goldstock.util.Lang;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** GoldStock — a simulated stock market traded with a physical item as currency. */
public final class GoldStockPlugin extends JavaPlugin {

    private final Lang lang = new Lang();
    private final Map<UUID, String> names = new HashMap<>();

    private MarketSettings settings;
    private CurrencySettings currency;
    private NewsSettings newsSettings;
    private IndicatorSettings indicatorSettings;
    private Database database;
    private MarketManager market;
    private NewsManager news;
    private PortfolioManager portfolios;
    private CurrencyService economy;
    private TradeService trades;
    private MenuManager menus;
    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readSettings();

        database = new Database(this);
        try {
            database.open();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "無法開啟資料庫，插件停用。", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        market = new MarketManager(this);
        news = new NewsManager(this);
        portfolios = new PortfolioManager(this);
        economy = new CurrencyService(this);
        trades = new TradeService(this);
        menus = new MenuManager(this);

        market.loadStocks(getConfig(), settings);
        restoreMarketState();
        news.load(database.loadActiveNews());
        portfolios.loadAll(database.loadHoldings());
        names.putAll(database.loadPlayerNames());

        PluginCommand command = getCommand("stock");
        if (command != null) {
            StockCommand executor = new StockCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("plugin.yml 沒有註冊 /stock 指令。");
        }

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        startTicking();
        getLogger().info("已載入 " + market.size() + " 檔股票，每 "
                + settings.updateIntervalSeconds() + " 秒更新一次股價。");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (market != null && database != null) {
            for (Stock stock : market.stocks()) {
                database.savePrice(stock.symbol(), stock.price(), stock.previousPrice());
            }
        }
        if (database != null) {
            database.close();
        }
    }

    /** Re-reads config.yml: settings, messages, stock definitions and the update interval. */
    public void reloadEverything() {
        reloadConfig();
        readSettings();
        market.loadStocks(getConfig(), settings);
        restoreMarketState();
        news.pruneUnknownStocks();
        startTicking();
        menus.refreshOpenMenus();
    }

    private void readSettings() {
        settings = MarketSettings.from(getConfig());
        currency = CurrencySettings.from(getConfig(), getLogger());
        newsSettings = NewsSettings.from(getConfig());
        indicatorSettings = IndicatorSettings.from(getConfig());
        lang.load(getConfig());
        lang.currencyName(currency.displayName());
    }

    /** Applies the stored price and history to any stock that does not have a live price yet. */
    private void restoreMarketState() {
        Map<String, double[]> storedPrices = database.loadPrices();
        Map<String, List<Double>> storedHistory = database.loadHistory(settings.historyPoints());
        for (Stock stock : market.stocks()) {
            double[] stored = storedPrices.get(stock.symbol());
            if (stored != null && stored[0] > 0.0) {
                stock.restore(stored[0], stored[1]);
            }
            List<Double> history = storedHistory.get(stock.symbol());
            if (history != null && !history.isEmpty() && stock.history().isEmpty()) {
                stock.loadHistory(history);
            }
        }
    }

    private void startTicking() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        long period = Math.max(20L, settings.updateIntervalTicks());
        tickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                market.tick();
            } catch (RuntimeException e) {
                getLogger().log(Level.SEVERE, "更新股價時發生錯誤", e);
            }
        }, period, period);
    }

    // ------------------------------------------------------------------ accessors

    public MarketSettings settings() {
        return settings;
    }

    public CurrencySettings currency() {
        return currency;
    }

    public NewsSettings newsSettings() {
        return newsSettings;
    }

    public IndicatorSettings indicators() {
        return indicatorSettings;
    }

    public Lang lang() {
        return lang;
    }

    public Database database() {
        return database;
    }

    public MarketManager market() {
        return market;
    }

    public NewsManager news() {
        return news;
    }

    public PortfolioManager portfolios() {
        return portfolios;
    }

    public CurrencyService economy() {
        return economy;
    }

    public TradeService trades() {
        return trades;
    }

    public MenuManager menus() {
        return menus;
    }

    // ------------------------------------------------------------------ name cache

    public void rememberName(UUID uuid, String name) {
        String previous = names.put(uuid, name);
        if (!name.equals(previous)) {
            database.savePlayerName(uuid, name);
        }
    }

    public String playerName(UUID uuid) {
        return names.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    /** Finds a player by name among online players first, then the stored names. */
    public UUID lookupPlayer(String name) {
        var online = getServer().getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
