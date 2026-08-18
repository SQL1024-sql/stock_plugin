package io.github.sql1024.goldstock.news;

import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

/** The {@code news} section of config.yml. */
public record NewsSettings(
        boolean enabled,
        double chancePercent,
        double accuracyPercent,
        int durationUpdates,
        double impactMultiplier,
        int maxActive,
        boolean broadcast,
        int keepHistory,
        List<String> bullishHeadlines,
        List<String> bearishHeadlines) {

    private static final List<String> FALLBACK_BULLISH = List.of("{name} 傳出利多消息");
    private static final List<String> FALLBACK_BEARISH = List.of("{name} 傳出利空消息");

    public static NewsSettings from(FileConfiguration config) {
        List<String> bullish = config.getStringList("news.headlines.bullish");
        List<String> bearish = config.getStringList("news.headlines.bearish");
        return new NewsSettings(
                config.getBoolean("news.enabled", true),
                Math.clamp(config.getDouble("news.chance-percent", 25.0), 0.0, 100.0),
                Math.clamp(config.getDouble("news.accuracy-percent", 70.0), 0.0, 100.0),
                Math.clamp(config.getInt("news.duration-updates", 3), 1, 60),
                Math.clamp(config.getDouble("news.impact-multiplier", 0.7), 0.0, 5.0),
                Math.clamp(config.getInt("news.max-active", 3), 1, 32),
                config.getBoolean("news.broadcast", true),
                Math.clamp(config.getInt("news.keep-history", 10), 1, 50),
                bullish.isEmpty() ? FALLBACK_BULLISH : List.copyOf(bullish),
                bearish.isEmpty() ? FALLBACK_BEARISH : List.copyOf(bearish));
    }
}
