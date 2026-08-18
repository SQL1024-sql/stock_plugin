# GoldStock — 玉髓錠股市

用**玉髓錠**（或任何你指定的物品）交易的模擬股市插件。
股價由插件自己模擬，不需要連外網、不需要 Vault、不需要任何其他插件。

- **伺服器**：Paper **26.1.2**（`api-version: 26.1`，26.2 也可以跑）
- **Java**：**25 或以上**（Paper 26.1 的最低要求）
- **貨幣**：玩家背包裡的實體物品，預設 `GOLD_INGOT` + 名稱「玉髓錠」（見下方設定）
- **儲存**：SQLite（`plugins/GoldStock/goldstock.db`），重開機股價、持股、新聞都會留著

> ⚠ **所有股價都是模擬出來的**，跟真實股市完全無關，也不會連到任何交易所。
> 只是借用大家熟悉的公司名稱與代號當作遊戲內容，別拿來當投資參考。

---

## 安裝

1. 把 `GoldStock-1.0.0.jar` 丟進伺服器的 `plugins/` 資料夾
2. 重啟伺服器
3. 編輯 `plugins/GoldStock/config.yml`，再用 `/stock reload` 生效

---

## 股票清單

| 代號 | 名稱 | 波動 | 特性 |
|---|---|---|---|
| `AAPL` | 蘋果 Apple | 低 | 穩定緩漲 |
| `NVDA` | 輝達 NVIDIA | 高 | 漲勢最強，但很晃 |
| `TSLA` | 特斯拉 Tesla | 很高 | 最刺激，方向難抓 |
| `MSFT` | 微軟 Microsoft | 低 | 高價穩定股 |
| `GOOGL` | Alphabet Google | 中 | 中規中矩 |
| `AMZN` | 亞馬遜 Amazon | 中 | 中規中矩 |
| `2330` | 台積電 | 中 | 台股權值王，緩漲 |
| `2317` | 鴻海 | 中 | 中規中矩 |
| `2454` | 聯發科 | 中高 | 高價高波動 |
| `2603` | 長榮 | 高 | 航運股，大起大落 |
| `2412` | 中華電 | 極低 | 定存股，幾乎不動 |
| `0050` | 元大台灣50 | 低 | ETF，最適合新手 |

台股代號在 config.yml 裡要加引號（`'2330'`），指令則直接打 `/stock info 2330`。
嫌股價太貴，就把該檔的 `initial-price` 和 `min-price` / `max-price` 一起等比例調小。

---

## 玩法

打 `/stock` 開啟股市介面（6 排箱子選單）：

| 操作 | 效果 |
|---|---|
| 左鍵 | 買 1 股 |
| Shift + 左鍵 | 買 10 股 |
| 右鍵 | 賣 1 股 |
| Shift + 右鍵 | 賣 10 股 |
| 中鍵 | 在聊天欄看詳細資訊與走勢 |
| 📰 書 | 財經新聞 |
| 中間的箱子 | 切換到「我的投資組合」 |

在投資組合介面裡：左鍵賣 1 股、**Shift + 左鍵 全部賣出**、右鍵買 1 股、Shift + 右鍵買 10 股。

每檔股票的圖示上會顯示現價、漲跌、價格區間、走勢圖（`▁▂▃▄▅▆▇█`）、
均線與技術面、目前的新聞，還有你的持股與損益。

### 錢怎麼進出

- **買入**：直接從背包扣掉貨幣物品。不夠就買不了。
- **賣出**：貨幣直接進背包；背包滿了剩下的會掉在腳邊（會標記歸屬給你）。
- 只計算**主背包 + 快捷欄**，盔甲欄與副手不算。

---

## 兩種「指標」：新聞與技術面

### 📰 財經新聞 —— 唯一能稍微預測未來的東西

每次股價更新後，有機率跳出一則新聞，指向某一檔股票的**利多**或**利空**。
新聞會在接下來幾次更新裡，給那檔股票一點額外的漲跌傾向。

重點是它**只是傾向，不是保證**：

- `accuracy-percent`（預設 70）決定新聞說的方向有多少機率是真的，
  剩下的 30% 就是**假消息**，價格會往反方向走；
- 就算新聞是真的，隨機波動仍然可能蓋過它；
- 新聞是在股價更新**之後**才發布的，所以你有一次更新的時間可以反應。

結果就是：長期跟著新聞下注會贏，但單筆還是常常輸。這是刻意設計的。

用 `/stock news` 或點介面裡的 📰 看目前有哪些新聞、還剩幾次更新。

想調鬆調緊：

| 設定 | 效果 |
|---|---|
| `news.accuracy-percent` | 50 = 純亂猜，100 = 穩賺（不建議） |
| `news.chance-percent` | 每次更新出現新聞的機率 |
| `news.duration-updates` | 一則新聞影響幾次更新 |
| `news.impact-multiplier` | 力道：偏移量 = 這個數字 × 該股波動率 × 隨機 0.6~1.4（上限 5.0） |
| `news.headlines` | 標題文案，`{name}` 會換成公司名 |

### 📈 技術面 —— 純粹是過去的數字

均線（預設 MA5 / MA20）、多空排列、近 20 次更新的動能。
這些**完全由歷史股價算出來，不含任何未來資訊**，只是讓走勢好讀一點。
不想要就把 `indicators.enabled` 設成 `false`。

---

## 指令

| 指令 | 說明 |
|---|---|
| `/stock` | 開啟股市介面 |
| `/stock list` | 所有股票、現價、漲跌、迷你走勢圖 |
| `/stock info <代號>` | 單檔詳細資訊、走勢、技術面、新聞、你的持股 |
| `/stock news` | 目前與最近的財經新聞 |
| `/stock buy <代號> <股數>` | 買入 |
| `/stock sell <代號> <股數\|all>` | 賣出（`all` = 全部賣出） |
| `/stock portfolio` | 我的投資組合與未實現損益 |
| `/stock log [筆數]` | 我最近的交易紀錄（預設 10 筆，最多 50） |
| `/stock top` | 持股市值排行榜（前 10 名） |

管理指令：

| 指令 | 說明 |
|---|---|
| `/stock reload` | 重新載入 `config.yml`（保留現有股價與持股） |
| `/stock setprice <代號> <價格>` | 手動設定股價 |
| `/stock tick` | 立刻更新一次股價 |
| `/stock portfolio <玩家>` | 查看別人的持股 |

別名：`/gs`、`/market`、`/stocks`。

## 權限

| 權限 | 預設 | 說明 |
|---|---|---|
| `goldstock.use` | 所有人 | 看盤、買賣、看自己的持股 |
| `goldstock.top` | 所有人 | 排行榜 |
| `goldstock.admin` | OP | `reload` / `setprice` / `tick` / 查看他人持股 |

---

## 貨幣設定

**「玉髓錠」不是 Minecraft 26.1／26.2 的原版物品**（我查過這兩版的 Material 清單，
新增的只有 26.2 的 `CINNABAR`、`SULFUR` 那批，沒有玉髓）。
所以貨幣做成可設定的，原版物品或其他插件的自訂道具都能用：

```yaml
currency:
  material: GOLD_INGOT          # 基底材質（原版 Material 名稱）
  display-name: '<gradient:#8fd3c7:#d9f2ec>玉髓錠</gradient>'   # 訊息裡顯示的名稱
  match: name                   # 怎麼認出「這顆是錢」
  match-name: '玉髓錠'           # match: name 時，物品名稱要完全等於這個
  match-item-model: ''          # match: item-model 時填 item_model 的 key
```

`match` 的四種模式：

| 模式 | 意思 |
|---|---|
| `vanilla` | 乾淨的原版物品（沒有自訂名稱／lore／附魔／自訂模型），不會吃掉玩家的特殊道具 |
| `name` | 物品名稱必須等於 `match-name` —— **給其他插件做的自訂道具用** |
| `item-model` | `item_model` 必須等於 `match-item-model` |
| `any` | 只要材質對就算（連有名字的都會被扣，慎用） |

賣股票付錢時，插件會照同一套規則產生物品（`name` 模式就產生帶那個名字的物品），
所以拿到的錢一定認得回來。

> 如果你伺服器上的玉髓錠底材不是金錠，把 `material` 改掉就好，不用改程式。

---

## 其他設定（`config.yml`）

### `market`

| 欄位 | 預設 | 說明 |
|---|---|---|
| `update-interval-seconds` | `300` | 每幾秒更新一次股價 |
| `max-tick-change-percent` | `18.0` | 單次更新的漲跌上限，避免價格暴衝 |
| `history-points` | `120` | 每檔股票保留幾個歷史價格點（走勢圖與均線用） |
| `fee-percent` | `1.0` | 手續費：買入加收、賣出扣除 |
| `broadcast-updates` | `false` | 每次更新是否全服廣播漲跌摘要 |
| `announce-big-moves-percent` | `10.0` | 單次漲跌超過此 % 就全服廣播該檔（`0` = 關閉） |

### `limits`

| 欄位 | 預設 | 說明 |
|---|---|---|
| `max-shares-per-trade` | `5000` | 單筆交易股數上限 |
| `max-shares-per-stock` | `20000` | 每人每檔持股上限 |
| `max-gold-per-transaction` | `100000` | 單筆金額上限，避免一次吐出幾千組物品 |

### 新增一檔股票

```yaml
stocks:
  MY:                                  # 代號，就是指令要打的字（不分大小寫）
    display-name: '<gold>我的公司'      # 支援 MiniMessage 標籤
    icon: GOLD_BLOCK                   # GUI 圖示的 Material
    initial-price: 500.0               # 初始股價（每股要幾顆貨幣）
    volatility: 0.06                   # 波動率：0.02 很穩、0.08 很刺激
    drift: 0.0008                      # 長期趨勢：正數緩漲、負數緩跌
    min-price: 80.0
    max-price: 9000.0
```

`/stock reload` 之後就會出現。從 config 移除一檔股票時，玩家原本的持股會留在資料庫裡
（`/stock portfolio` 會標記「這檔股票已從設定移除」），把它加回來就能繼續交易。

### 訊息

`messages:` 區段全部可以改，格式是
[MiniMessage](https://docs.advntr.dev/minimessage/format.html)，
佔位符用 `{大括號}`；`{currency}` 會自動換成貨幣名稱。

---

## 股價是怎麼算的

每次更新，每檔股票各自跑一步幾何隨機漫步：

```
bias   = 目前新聞的偏移量（沒新聞就是 0）
factor = exp(drift + bias + volatility × 常態隨機值)
factor = clamp(factor, 1 − 上限, 1 + 上限)     # max-tick-change-percent
price  = clamp(price × factor, min-price, max-price)
```

`volatility` 決定振幅、`drift` 決定長期方向、`bias` 是新聞造成的短期傾向。
玩家買賣**不會**影響股價（想要供需模型的話，就是改 `MarketManager.tick()` 這一段）。

金額一律是整數：**買入向上取整、賣出向下取整**，
所以在同一價位買了立刻賣掉一定會小虧（手續費 + 進位差），這是刻意的。

---

## 從原始碼建置

需要 JDK 25：

```bash
mvn clean package
# 產出：target/GoldStock-1.0.0.jar（已內含 sqlite-jdbc）
```

## 專案結構

```
src/main/java/io/github/sql1024/goldstock/
├── GoldStockPlugin.java          # 進入點、設定載入、排程、名稱快取
├── market/                       # Stock、價格引擎、技術指標
├── news/                         # 新聞事件與發布邏輯
├── economy/                      # 貨幣物品的辨識／扣除／給予
├── portfolio/                    # Holding（含成本基礎）、PortfolioManager
├── trade/TradeService.java       # 買賣驗證與執行
├── storage/Database.java         # SQLite（單一寫入執行緒，遊戲執行緒不卡）
├── gui/                          # 箱子介面（股市 / 投資組合 / 事件）
├── command/StockCommand.java     # /stock 全部子指令與 Tab 補全
└── util/                         # 訊息、數字格式、走勢圖、聊天報表
```

## 已驗證

- 在真實的 Paper 26.1.2（build 74）+ Java 25 上載入、啟用、停用，沒有任何錯誤
- 12 檔股票（含 `2330`、`0050` 這種純數字代號）都正常載入與查詢
- 強制跑 30 次股價更新：新聞會發布、會過期、會顯示在 `/stock news` 與 GUI
- 均線、多空排列、動能在累積足夠歷史後正確出現
- **新聞方向驗證**：把準確率設成 100% 跑 14 次更新，13 次「下一次更新」的漲跌方向
  全部符合新聞方向；發布新聞前的那一次則是純隨機 —— 確認新聞確實會影響股價，
  而且是「先看到新聞、下一次更新才生效」
- 重開伺服器後股價、歷史紀錄與未結束的新聞都正確還原
- 手續費、取整方向、平均成本、部分賣出的成本基礎分攤都有數學驗證
