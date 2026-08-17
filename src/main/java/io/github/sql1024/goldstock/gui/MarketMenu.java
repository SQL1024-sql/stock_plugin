package io.github.sql1024.goldstock.gui;

import java.util.ArrayList;
import java.util.List;

import io.github.sql1024.goldstock.GoldStockPlugin;
import io.github.sql1024.goldstock.market.Stock;
import io.github.sql1024.goldstock.portfolio.Holding;
import io.github.sql1024.goldstock.util.Fmt;
import io.github.sql1024.goldstock.util.Lang;
import io.github.sql1024.goldstock.util.Report;
import io.github.sql1024.goldstock.util.Sparkline;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** The main board: every stock, its price, its chart, and click-to-trade. */
public final class MarketMenu extends StockMenu {

    public MarketMenu(GoldStockPlugin plugin, Player viewer) {
        super(plugin, viewer, Lang.mini(plugin.settings().guiTitle()));
    }

    @Override
    public void render() {
        List<Stock> stocks = plugin.market().stockList();
        clampPage(stocks.size());
        slotSymbols.clear();

        long carried = plugin.economy().count(viewer);
        drawFrame(stocks.size(), "<gold>我的持股", List.of(
                "<gray>身上金錠：<gold>" + Fmt.gold(carried),
                "<gray>持股市值：<gold>" + Fmt.gold(plugin.portfolios().marketValue(viewer.getUniqueId())),
                "<yellow>點一下查看投資組合"));

        int from = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int index = from + i;
            if (index >= stocks.size()) {
                break;
            }
            Stock stock = stocks.get(index);
            int slot = CONTENT_SLOTS[i];
            Holding holding = plugin.portfolios().get(viewer.getUniqueId(), stock.symbol());
            inventory.setItem(slot, buildIcon(stock, holding, carried));
            slotSymbols.put(slot, stock.symbol());
        }
    }

    private org.bukkit.inventory.ItemStack buildIcon(Stock stock, Holding holding, long carried) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>代號 <white>" + stock.symbol());
        lore.add("<gray>現價 <gold>" + Fmt.price(stock.price()) + "</gold> <gray>金錠 / 股");
        lore.add("<gray>漲跌 " + Fmt.changeTag(stock.changePercent())
                + "<reset><gray>　累計 " + Fmt.changeTag(stock.sessionChangePercent()));
        lore.add("<gray>區間 <white>" + Fmt.price(stock.historyLow())
                + "</white> ~ <white>" + Fmt.price(stock.historyHigh()));
        lore.add("<gray>走勢 " + Sparkline.render(stock.history(), plugin.settings().chartWidth()));
        lore.add("<dark_gray>");

        long unitCost = plugin.settings().buyCost(stock.price(), 1);
        lore.add("<gray>買 1 股 <gold>" + Fmt.gold(unitCost) + "</gold> <gray>金錠"
                + (carried >= unitCost ? "" : "　<red>(金錠不足)"));

        if (holding.shares() > 0) {
            long value = (long) Math.floor(stock.price() * holding.shares());
            lore.add("<gray>持有 <white>" + holding.shares() + "</white> 股　均價 <white>"
                    + Fmt.price(holding.averageCost()));
            lore.add("<gray>市值 <gold>" + Fmt.gold(value) + "</gold> <gray>金錠　損益 "
                    + Fmt.pnlTag(value - holding.invested()));
        }

        lore.add("<dark_gray>");
        lore.add("<yellow>左鍵 <gray>買 1　<yellow>Shift+左鍵 <gray>買 10");
        lore.add("<yellow>右鍵 <gray>賣 1　<yellow>Shift+右鍵 <gray>賣 10");
        lore.add("<yellow>中鍵 <gray>詳細資訊");

        int amount = holding.shares() > 0 ? holding.shares() : 1;
        return Icons.of(stock.icon(), amount, stock.displayName() + " <dark_gray>[" + stock.symbol() + "]", lore);
    }

    @Override
    public void onClick(int slot, ClickType click) {
        switch (slot) {
            case SLOT_PREVIOUS -> {
                page--;
                click(Sound.UI_BUTTON_CLICK);
                render();
                return;
            }
            case SLOT_NEXT -> {
                page++;
                click(Sound.UI_BUTTON_CLICK);
                render();
                return;
            }
            case SLOT_REFRESH -> {
                click(Sound.UI_BUTTON_CLICK);
                render();
                return;
            }
            case SLOT_SWITCH -> {
                plugin.menus().openPortfolio(viewer);
                return;
            }
            default -> {
                // fall through to the stock slots
            }
        }

        String symbol = slotSymbols.get(slot);
        if (symbol == null) {
            return;
        }
        Stock stock = plugin.market().stock(symbol);
        if (stock == null) {
            render();
            return;
        }

        switch (click) {
            case LEFT -> plugin.trades().buy(viewer, stock, 1);
            case SHIFT_LEFT -> plugin.trades().buy(viewer, stock, 10);
            case RIGHT -> plugin.trades().sell(viewer, stock, 1);
            case SHIFT_RIGHT -> plugin.trades().sell(viewer, stock, 10);
            case MIDDLE -> {
                for (Component line : Report.stockInfo(plugin, stock, viewer.getUniqueId())) {
                    viewer.sendMessage(line);
                }
                click(Sound.ITEM_BOOK_PAGE_TURN);
            }
            default -> {
                return;
            }
        }
        render();
    }

    private void click(Sound sound) {
        viewer.playSound(viewer.getLocation(), sound, 0.5f, 1.4f);
    }
}
