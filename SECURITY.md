# Security notes

## Scope
TradeQuest is a client-only Android training app. It has no application backend, account server, payment endpoint, or trade-execution endpoint. The only network integration is read-only public market data from Bybit. Therefore a DDoS test against a production endpoint is not applicable and must not be run against Bybit or any third party.

## Implemented safeguards
- Android backup is disabled; cleartext HTTP is disabled in the manifest.
- No exchange API keys, passwords, signing keys, or keystores are stored by the app.
- Market-data requests use HTTPS, bounded connect/read timeouts, a 2 MB response cap, a fixed host, and no redirects.
- Provider candle rows are validated for finite positive OHLC values, consistent high/low bounds, non-negative volume, and positive timestamps.
- Local trade history is capped at 1,000 records when persisted.
- Registration requires both consent checkboxes; legal-document navigation has no route to the main screen before profile creation.
- The app has no real order execution or money custody.

## Safe verification
- Run the local JVM unit tests with `gradle :app:testDebugUnitTest`.
- Build with `gradle :app:assembleDebug`.
- Exercise malformed inputs, empty/oversized provider responses, interrupted connections, repeated navigation, rapid button taps, and app process recreation on a local emulator.
- Any future load test must target an isolated staging service owned by the project, with explicit rate limits and a stop condition. Never flood public APIs or shared infrastructure.

## Remaining limitations
- Profile and progress are local SharedPreferences data, not server-authenticated accounts. Rooted devices or a compromised OS can expose local data.
- The app does not currently use encrypted local storage because no credentials or sensitive server tokens are held.
- Debug APKs are for testing. A production release must be signed with a private release key kept outside source control.
