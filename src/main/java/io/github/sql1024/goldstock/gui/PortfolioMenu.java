package io.github.sql1024.goldstock.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.sql1024.goldstock.GoldStockPlugin;
import io.github.sql1024.goldstock.market.Stock;
import io.github.sql1024.goldstock.portfolio.Holding;
import io.github.sql1024.goldstock.util.Fmt;
import io.github.sql1024.goldstock.util.Lang;
import io.github.sql1024.goldstock.util.Report;
import io.github.sql1024.goldstock.util.Sparkline;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** The player's own positions, with sell-from-here controls. */
public final class PortfolioMenu extends StockMenu {

    public PortfolioMenu(GoldStockPlugin plugin, Player viewer) {
        super(plugin, viewer, Lang.mini("<gold>我的投資組合"));
    }

    @Override
    public void render() {
        List<Map.Entry<String, Holding>> positions = new ArrayList<>();
        for (Map.Entry<String, Holding> entry : plugin.portfolios().all(viewer.getUniqueId()).entrySet()) {
            if (plugin.market().has(entry.getKey()) && entry.getValue().shares() > 0) {
                positions.add(entry);
            }
        }

        clampPage(positions.size());
        slotSymbols.clear();

        long carried = plugin.economy().count(viewer);
        long value = plugin.portfolios().marketValue(viewer.getUniqueId());
        long invested = plugin.portfolios().investedTotal(viewer.getUniqueId());

        drawFrame(positions.size(), "<aqua>← 回到股市", List.of(
                "<gray>身上金錠：<gold>" + Fmt.gold(carried),
                "<gray>持股市值：<gold>" + Fmt.gold(value),
                "<gray>投入成本：<gold>" + Fmt.gold(invested),
                "<gray>未實現損益：" + Fmt.pnlTag(value - invested),
                "<gray>總資產：<gold>" + Fmt.gold(carried + value),
                "<yellow>點一下回到股市"));

        if (positions.isEmpty()) {
            inventory.setItem(CONTENT_SLOTS[CONTENT_SLOTS.length / 2], Icons.of(Material.BARRIER,
                    "<red>你還沒有任何持股",
                    List.of("<gray>回到股市買第一檔股票吧！")));
            return;
        }

        int from = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int index = from + i;
            if (index >= positions.size()) {
                break;
            }
            Map.Entry<String, Holding> entry = positions.get(index);
            Stock stock = plugin.market().stock(entry.getKey());
            if (stock == null) {
                continue;
            }
            int slot = CONTENT_SLOTS[i];
            inventory.setItem(slot, buildIcon(stock, entry.getValue()));
            slotSymbols.put(slot, stock.symbol());
        }
    }

    private ItemStack buildIcon(Stock stock, Holding holding) {
        long value = (long) Math.floor(stock.price() * holding.shares());
        long payout = plugin.settings().sellProceeds(stock.price(), holding.shares());

        List<String> lore = new ArrayList<>();
        lore.add("<gray>持有 <white>" + holding.shares() + "</white> 股　均價 <white>"
                + Fmt.price(holding.averageCost()));
        lore.add("<gray>現價 <gold>" + Fmt.price(stock.price()) + "</gold> <gray>金錠　"
                + Fmt.changeTag(stock.changePercent()));
        lore.add("<gray>市值 <gold>" + Fmt.gold(value) + "</gold> <gray>金錠");
        lore.add("<gray>投入 <gold>" + Fmt.gold(holding.invested()) + "</gold> <gray>金錠");
        lore.add("<gray>未實現損益 " + Fmt.pnlTag(value - holding.invested()));
        lore.add("<gray>全部賣出可得 <gold>" + Fmt.gold(payout) + "</gold> <gray>金錠");
        lore.add("<gray>走勢 " + Sparkline.render(stock.history(), plugin.settings().chartWidth()));
        lore.add("<dark_gray>");
        lore.add("<yellow>左鍵 <gray>賣 1　<yellow>Shift+左鍵 <gray>全部賣出");
        lore.add("<yellow>右鍵 <gray>買 1　<yellow>Shift+右鍵 <gray>買 10");
        lore.add("<yellow>中鍵 <gray>詳細資訊");

        return Icons.of(stock.icon(), holding.shares(),
                stock.displayName() + " <dark_gray>[" + stock.symbol() + "]", lore);
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
                plugin.menus().openMarket(viewer);
                return;
            }
            default -> {
                // fall through to the position slots
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
            case LEFT -> plugin.trades().sell(viewer, stock, 1);
            case SHIFT_LEFT -> plugin.trades().sellAll(viewer, stock);
            case RIGHT -> plugin.trades().buy(viewer, stock, 1);
            case SHIFT_RIGHT -> plugin.trades().buy(viewer, stock, 10);
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
