# GoldStock — 金錠股市

用**金錠**交易的模擬股市插件。股價由插件自己模擬（隨機漫步 + 波動率 + 趨勢），
不需要連外網、不需要 Vault、不需要任何其他插件。

- **伺服器**：Paper **26.1.2**（`api-version: 26.1`，26.2 也可以跑）
- **Java**：**25 或以上**（Paper 26.1 的最低要求）
- **貨幣**：玩家背包裡的**實體金錠**（`GOLD_INGOT`）
- **儲存**：SQLite（`plugins/GoldStock/goldstock.db`），重開機股價與持股都會留著

---

## 安裝

1. 把 `GoldStock-1.0.0.jar` 丟進伺服器的 `plugins/` 資料夾
2. 重啟伺服器
3. 編輯 `plugins/GoldStock/config.yml`，再用 `/stock reload` 生效

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
| 中間的箱子 | 切換到「我的投資組合」 |

在投資組合介面裡：左鍵賣 1 股、**Shift + 左鍵 全部賣出**、右鍵買 1 股、Shift + 右鍵買 10 股。

每檔股票的圖示上會顯示現價、漲跌、價格區間、走勢圖（`▁▂▃▄▅▆▇█`）、你的持股與損益。

### 金錠怎麼進出

- **買入**：直接從背包扣掉金錠。金錠不夠就買不了。
- **賣出**：金錠直接進背包；背包滿了剩下的會掉在腳邊（會標記歸屬給你）。
- 只計算**主背包 + 快捷欄**的金錠，盔甲欄與副手不算。
- `currency.strict-items: true`（預設）時，有**自訂名稱／lore／附魔／自訂模型**的金錠
  不會被當成錢扣掉，避免吃掉玩家的特殊道具。

---

## 指令

| 指令 | 說明 |
|---|---|
| `/stock` | 開啟股市介面 |
| `/stock list` | 所有股票、現價、漲跌、迷你走勢圖 |
| `/stock info <代號>` | 單檔詳細資訊、走勢、你的持股 |
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
| `/stock tick` | 立刻更新一次所有股價 |
| `/stock portfolio <玩家>` | 查看別人的持股 |

別名：`/gs`、`/market`、`/stocks`。

## 權限

| 權限 | 預設 | 說明 |
|---|---|---|
| `goldstock.use` | 所有人 | 看盤、買賣、看自己的持股 |
| `goldstock.top` | 所有人 | 排行榜 |
| `goldstock.admin` | OP | `reload` / `setprice` / `tick` / 查看他人持股 |

---

## 設定說明（`config.yml`）

### `market`

| 欄位 | 預設 | 說明 |
|---|---|---|
| `update-interval-seconds` | `300` | 每幾秒更新一次股價 |
| `max-tick-change-percent` | `18.0` | 單次更新的漲跌上限，避免價格暴衝 |
| `history-points` | `120` | 每檔股票保留幾個歷史價格點（走勢圖用） |
| `fee-percent` | `1.0` | 手續費：買入加收、賣出扣除 |
| `broadcast-updates` | `false` | 每次更新是否全服廣播漲跌摘要 |
| `announce-big-moves-percent` | `10.0` | 單次漲跌超過此 % 就全服廣播該檔（`0` = 關閉） |

### `limits`

| 欄位 | 預設 | 說明 |
|---|---|---|
| `max-shares-per-trade` | `5000` | 單筆交易股數上限 |
| `max-shares-per-stock` | `20000` | 每人每檔持股上限 |
| `max-gold-per-transaction` | `100000` | 單筆金額上限，避免一次吐出幾千組金錠 |

### 新增一檔股票

```yaml
stocks:
  MY:                                  # 代號，就是指令要打的字（不分大小寫）
    display-name: '<gold>我的公司'      # 支援 MiniMessage 標籤
    icon: GOLD_BLOCK                   # GUI 圖示的 Material
    initial-price: 500.0               # 初始股價（金錠／股）
    volatility: 0.06                   # 波動率：0.02 很穩、0.10 很刺激
    drift: 0.0008                      # 長期趨勢：正數緩漲、負數緩跌
    min-price: 80.0
    max-price: 9000.0
```

`/stock reload` 之後就會出現。從 config 移除一檔股票時，玩家原本的持股會留在資料庫裡
（`/stock portfolio` 會標記「這檔股票已從設定移除」），把它加回來就能繼續交易。

### 訊息

`messages:` 區段全部可以改，格式是
[MiniMessage](https://docs.advntr.dev/minimessage/format.html)，
佔位符用 `{大括號}`（例如 `{symbol}`、`{shares}`、`{gold}`）。

---

## 股價是怎麼算的

每次更新，每檔股票各自跑一步幾何隨機漫步：

```
factor = exp(drift + volatility × 常態隨機值)
factor = clamp(factor, 1 − 上限, 1 + 上限)     # max-tick-change-percent
price  = clamp(price × factor, min-price, max-price)
```

所以 `volatility` 決定振幅、`drift` 決定長期方向，`min-price` / `max-price` 是硬邊界。
玩家買賣**不會**影響股價（如果想要供需模型，就是改 `MarketManager.tick()` 這一段）。

金額一律是整數金錠：**買入向上取整、賣出向下取整**，
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
├── market/                       # Stock、MarketSettings、MarketManager（價格引擎）
├── economy/GoldEconomy.java      # 實體金錠的計算／扣除／給予
├── portfolio/                    # Holding（含成本基礎）、PortfolioManager
├── trade/TradeService.java       # 買賣驗證與執行
├── storage/Database.java         # SQLite（單一寫入執行緒，遊戲執行緒不卡）
├── gui/                          # 箱子介面（MarketMenu / PortfolioMenu / 事件）
├── command/StockCommand.java     # /stock 全部子指令與 Tab 補全
└── util/                         # 訊息、數字格式、走勢圖、聊天報表
```

## 已驗證

- 在真實的 Paper 26.1.2（build 74）+ Java 25 上載入、啟用、停用，沒有任何錯誤
- 股價更新、`reload`、`setprice`、`tick`、錯誤處理路徑都實測過
- 重開伺服器後股價與歷史紀錄正確還原
- 手續費、取整方向、平均成本、部分賣出的成本基礎分攤都有數學驗證
