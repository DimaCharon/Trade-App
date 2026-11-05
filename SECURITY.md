# Security notes

- Do not add exchange API keys to this project. The app uses public market-data endpoints only.
- No trade execution capability is present.
- Do not commit access tokens, passwords, signing keys or keystores.
- The debug APK produced by CI is suitable for installation/testing, not store distribution. A production release must be signed with a private release key stored outside source control.
