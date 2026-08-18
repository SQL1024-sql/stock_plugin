package io.github.sql1024.goldstock.news;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import io.github.sql1024.goldstock.GoldStockPlugin;
import io.github.sql1024.goldstock.market.Stock;

/**
 * Publishes financial news and turns it into a temporary drift on the affected stock.
 *
 * <p>News is the only place in the plugin where the future leaks into the present, and it leaks on
 * purpose: a headline is right {@code accuracy-percent} of the time, and even when it is right the
 * random walk can still swamp it. Reading the news pays off across many trades, never on one.
 */
public final class NewsManager {

    private final GoldStockPlugin plugin;
    private final Random random = new Random();
    private final List<NewsEvent> active = new ArrayList<>();
    private final Deque<NewsEvent> history = new ArrayDeque<>();

    public NewsManager(GoldStockPlugin plugin) {
        this.plugin = plugin;
    }

    public List<NewsEvent> active() {
        return Collections.unmodifiableList(active);
    }

    /** Most recently published first: the active items, then the ones that have run their course. */
    public List<NewsEvent> recent() {
        List<NewsEvent> out = new ArrayList<>(active);
        out.addAll(history);
        return out;
    }

    public List<NewsEvent> activeFor(String symbol) {
        List<NewsEvent> out = new ArrayList<>(1);
        for (NewsEvent event : active) {
            if (event.symbol().equals(symbol)) {
                out.add(event);
            }
        }
        return out;
    }

    public boolean hasNews(String symbol) {
        for (NewsEvent event : active) {
            if (event.symbol().equals(symbol)) {
                return true;
            }
        }
        return false;
    }

    /** Extra log-return applied to this stock on the current market update. */
    public double biasFor(String symbol) {
        double bias = 0.0;
        for (NewsEvent event : active) {
            if (event.symbol().equals(symbol)) {
                bias += event.biasPerUpdate() * event.actualDirection();
            }
        }
        return bias;
    }

    /** Called once per market update, after prices have moved. Expires spent news. */
    public void afterUpdate() {
        boolean changed = false;
        for (NewsEvent event : new ArrayList<>(active)) {
            event.countDown();
            if (event.expired()) {
                active.remove(event);
                remember(event);
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    /**
     * Rolls for a new headline. Called after prices move, so players get one window to react
     * before the news starts biasing the price.
     */
    public void maybePublish() {
        NewsSettings settings = plugin.newsSettings();
        if (!settings.enabled() || active.size() >= settings.maxActive()) {
            return;
        }
        if (random.nextDouble() * 100.0 >= settings.chancePercent()) {
            return;
        }

        List<Stock> candidates = new ArrayList<>();
        for (Stock stock : plugin.market().stocks()) {
            if (!hasNews(stock.symbol())) {
                candidates.add(stock);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        Stock stock = candidates.get(random.nextInt(candidates.size()));
        int published = random.nextBoolean() ? 1 : -1;
        boolean truthful = random.nextDouble() * 100.0 < settings.accuracyPercent();
        int actual = truthful ? published : -published;

        List<String> pool = published > 0 ? settings.bullishHeadlines() : settings.bearishHeadlines();
        String headline = pool.get(random.nextInt(pool.size()))
                .replace("{name}", stock.displayName())
                .replace("{symbol}", stock.symbol());

        double strength = 0.6 + random.nextDouble() * 0.8;
        double bias = settings.impactMultiplier() * stock.volatility() * strength;

        NewsEvent event = new NewsEvent(stock.symbol(), headline, published, actual, bias,
                settings.durationUpdates(), System.currentTimeMillis());
        active.add(event);
        persist();
        broadcast(event, stock);
    }

    private void broadcast(NewsEvent event, Stock stock) {
        if (!plugin.newsSettings().broadcast()) {
            return;
        }
        String key = event.bullish() ? "news-bullish" : "news-bearish";
        plugin.getServer().broadcast(plugin.lang().msg(key,
                "symbol", stock.symbol(),
                "name", stock.displayName(),
                "headline", event.headline()));
    }

    private void remember(NewsEvent event) {
        history.addFirst(event);
        while (history.size() > plugin.newsSettings().keepHistory()) {
            history.removeLast();
        }
    }

    /** Restores news that was still running when the server stopped. */
    public void load(Collection<NewsEvent> stored) {
        active.clear();
        for (NewsEvent event : stored) {
            if (!event.expired() && plugin.market().has(event.symbol())) {
                active.add(event);
            }
        }
    }

    /** Drops news about stocks that no longer exist, e.g. after a config reload. */
    public void pruneUnknownStocks() {
        if (active.removeIf(event -> !plugin.market().has(event.symbol()))) {
            persist();
        }
    }

    private void persist() {
        plugin.database().saveActiveNews(List.copyOf(active));
    }
}
