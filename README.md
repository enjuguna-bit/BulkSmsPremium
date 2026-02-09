# BulkSmsPremium

Bulk SMS manager for Android with CSV/Excel import, templates, scheduling, and subscription gating.

## Features
- Bulk SMS sending with personalization templates
- CSV and Excel import with preview
- Scheduled sending and delivery tracking (WorkManager)
- Contact access and conversation UI
- Optional subscription status checks via an external billing endpoint
- Firebase SDK integrations available (Analytics/Auth/Firestore/Storage/FCM) when configured

## Tech Stack
- Kotlin + Java, ViewBinding
- Hilt, Room, WorkManager, Navigation
- Retrofit/OkHttp
- Firebase SDKs

## Requirements
- JDK 17
- Android SDK (compileSdk 34)
- Android Studio (recommended)

## Setup
1. Ensure `local.properties` points to your Android SDK.
2. Open the project in Android Studio and sync Gradle.

### Firebase (optional)
Firebase dependencies are included, but the Google Services plugin is commented out in `app/build.gradle`.
To enable Firebase:
1. Add `google-services.json` to `app/`.
2. Uncomment `id 'com.google.gms.google-services'` in `app/build.gradle`.

## Build
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

### Release signing
Release signing reads these Gradle properties:
`MYAPP_UPLOAD_STORE_FILE`, `MYAPP_UPLOAD_STORE_PASSWORD`, `MYAPP_UPLOAD_KEY_ALIAS`, `MYAPP_UPLOAD_KEY_PASSWORD`.

Set them in `~/.gradle/gradle.properties` or as environment variables before running a release build.

## Tests
```bash
./gradlew test
```

## Backend components
- `functions/`: Firebase Cloud Functions scaffold (currently exports no functions).
- `firebase-webhook/`: Intasend webhook handler (Node 18). Move secrets to environment config before deploying.
- `functions/src/billing.js`: Cloudflare Worker script for payment webhooks and subscription status. Update the endpoint in `app/src/main/java/com/bulksms/smsmanager/billing/SubscriptionHelper.kt` if you host your own.

## Repo layout
- `app/` Android application
- `READY_TO_INTEGRATE/` drop-in subscription UI/layout
- `scripts/` helper scripts
- `firebase-webhook/` webhook handler

## Permissions
The app requests SMS, contacts, notifications, and foreground service permissions required for bulk sending and delivery tracking. See `app/src/main/AndroidManifest.xml`.
