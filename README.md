# TradeQuest — Android historical trading trainer

A dark, animated Android training app prototype for Android 9.0+ (minSdk 28).

## What is implemented

- Native Android Java app; no webview, no external runtime dependency.
- Dark UI, animated screen transitions and press feedback.
- Dashboard with virtual balance, XP, lesson path and local progress.
- Real historical OHLCV candles fetched from Bybit V5 public market API (linear USDT perpetuals).
- Supported pairs: BTCUSDT, ETHUSDT, SOLUSDT, XRPUSDT, DOGEUSDT.
- Timeframes: 1m, 5m, 15m, 1h, 4h, 1d.
- A random decision point is selected from the loaded historical series. Candles after it are not displayed until a trade is confirmed.
- Entry is modeled at the open of the next candle to avoid look-ahead bias.
- LONG/SHORT, virtual margin, leverage, TP and SL inputs.
- The next historical candles are replayed and TP/SL/time-expiry is calculated from real OHLC values.
- Conservative handling when both TP and SL fall inside the same candle: SL is counted first and the result is flagged as ambiguous.
- Virtual balance, XP, number of trades, win rate and P&L are saved locally.
- No exchange account, API key, deposits, withdrawals or real order execution.

## Important modeling limits in this first build

This is a training simulator, not a live trading terminal. P&L is gross and excludes trading fees and funding. Maintenance margin, mark-price liquidation, slippage, partial fills and exchange-specific contract rules are not yet modeled. Therefore the displayed leverage and P&L are educational approximations and must not be used to size real trades. The chart and historical candles are sourced from Bybit's public API; no synthetic/demo candle fallback is shown when the network request fails.

The simulator uses candle OHLC ranges. When both TP and SL are touched during one candle, it conservatively assumes SL happened first because ordinary OHLC data does not reveal the intra-candle path.

## Build locally

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, Gradle 8.9.

```bash
gradle --no-daemon :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build on GitHub Actions

The workflow at `.github/workflows/android.yml` runs on push to `main`/`master` or manual dispatch. It builds `app-debug.apk` and uploads it as the `TradeQuest-Android-APK` workflow artifact. Open the repository's **Actions** tab, select **Build TradeQuest APK**, open the successful run and download the artifact.

## Data source

Bybit V5 market Kline endpoint:

`GET https://api.bybit.com/v5/market/kline?category=linear&symbol=BTCUSDT&interval=15&limit=1000`

The endpoint returns historical OHLCV candles and does not require a private API key.
