# Reminder (Business & Ledger Management)

An offline-first Android application designed for business professionals to seamlessly manage ledgers, track financial transactions, and schedule smart reminders for payments and compliance tasks.

## Features
- **Ledger Management**: Keep track of parties, clients, and their financial transactions with detailed logs.
- **Smart Reminders**: Schedule one-time or recurring reminders for payments, follow-ups, and compliance deadlines.
- **Offline First**: All data is securely stored on your device using a local, encrypted database structure. No internet connection is required.
- **Biometric Security**: Lock the app and secure your financial data with fingerprint or face unlock using Android's native Biometric APIs.
- **Encrypted Backups**: Export and restore secure, encrypted backups of your entire ledger using the Android Storage Access Framework (SAF).

## Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Architecture**: MVVM + Clean Architecture principles
- **Local Database**: Room Database
- **Security**: AndroidX Biometric, AndroidX Security Crypto (`EncryptedFile`)
- **Background Tasks**: `AlarmManager` for precise and reliable reminder notifications

## Privacy & Security
We prioritize user privacy. The app is completely offline-first, meaning your data never leaves your device unless you explicitly export a backup file yourself.
- Read the [Privacy Policy](PRIVACY_POLICY.md)
- Read the [Terms and Conditions](TERMS_AND_CONDITIONS.md)

## Screenshots
*(Add your app screenshots here before uploading to GitHub to showcase the UI!)*

## License
Copyright 2026. All rights reserved.