package io.github.sql1024.goldstock.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.sql1024.goldstock.GoldStockPlugin;
import io.github.sql1024.goldstock.market.Stock;
import io.github.sql1024.goldstock.portfolio.Holding;
import net.kyori.adventure.text.Component;

/** Chat renderings shared by the commands and the GUI. */
public final class Report {

    private Report() {
    }

    /** Detailed view of one stock, including the viewer's own position when they hold any. */
    public static List<Component> stockInfo(GoldStockPlugin plugin, Stock stock, UUID viewer) {
        int width = plugin.settings().chartWidth();
        List<Component> lines = new ArrayList<>();
        lines.add(Lang.mini("<dark_gray>▬▬▬▬▬▬ " + stock.displayName()
                + " <gray>[" + stock.symbol() + "]<dark_gray> ▬▬▬▬▬▬"));
        lines.add(Lang.mini("<gray>現價：<gold>" + Fmt.price(stock.price()) + "</gold> <gray>金錠 / 股　"
                + Fmt.changeTag(stock.changePercent())));
        lines.add(Lang.mini("<gray>區間：<white>" + Fmt.price(stock.historyLow())
                + "</white> ~ <white>" + Fmt.price(stock.historyHigh())
                + "</white>　<gray>累計 " + Fmt.changeTag(stock.sessionChangePercent())));
        lines.add(Lang.mini("<gray>走勢：" + Sparkline.render(stock.history(), width)));
        lines.add(Lang.mini("<gray>手續費：<white>" + Fmt.price(plugin.settings().feePercent()) + "%</white>"
                + "　<gray>買 1 股需 <gold>" + Fmt.gold(plugin.settings().buyCost(stock.price(), 1))
                + "</gold> <gray>金錠"));

        if (viewer != null) {
            Holding holding = plugin.portfolios().get(viewer, stock.symbol());
            if (holding.shares() > 0) {
                long value = (long) Math.floor(stock.price() * holding.shares());
                lines.add(Lang.mini("<gray>你的持股：<white>" + holding.shares()
                        + "</white> 股　均價 <white>" + Fmt.price(holding.averageCost())
                        + "</white>　市值 <gold>" + Fmt.gold(value)
                        + "</gold>　未實現 " + Fmt.pnlTag(value - holding.invested())));
            }
        }
        return lines;
    }

    /** One line per stock, for {@code /stock list}. */
    public static List<Component> marketList(GoldStockPlugin plugin, UUID viewer) {
        List<Component> lines = new ArrayList<>();
        lines.add(Lang.mini("<dark_gray>▬▬▬▬▬ <gold>金錠股市<dark_gray> ▬▬▬▬▬"));
        for (Stock stock : plugin.market().stocks()) {
            int held = viewer == null ? 0 : plugin.portfolios().shares(viewer, stock.symbol());
            String heldPart = held > 0 ? "　<dark_gray>(持有 <white>" + held + "</white> 股)" : "";
            lines.add(Lang.mini("<gray>" + stock.symbol() + " <dark_gray>· " + stock.displayName()
                    + "<gray>　<gold>" + Fmt.price(stock.price()) + "</gold> <gray>金錠　"
                    + Fmt.changeTag(stock.changePercent()) + "<reset>　"
                    + Sparkline.render(stock.history(), Math.min(12, plugin.settings().chartWidth()))
                    + heldPart));
        }
        lines.add(Lang.mini("<dark_gray>用 <yellow>/stock <dark_gray>開啟介面，"
                + "<yellow>/stock info 代號<dark_gray> 看詳細。"));
        return lines;
    }
}
