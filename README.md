# BulkSmsPremium

Bulk SMS manager for Android with CSV/Excel import, templates, scheduling, subscription gating, and full Kenyan carrier support.

---

## Features
- Bulk SMS sending with personalisation templates (`{{name}}`, `{{phone}}`, etc.)
- CSV and Excel import with live preview
- Scheduled sending and delivery tracking (WorkManager)
- Contact access and conversation inbox UI
- Subscription gating via Intasend / M-Pesa (1-Hour, 6-Hour, 24-Hour, Weekly, **Monthly** plans)
- Firebase SDK integrations: Analytics, Crashlytics, Firestore, FCM, Remote Config

---

## Tech Stack
- Kotlin + Java, ViewBinding
- Hilt 2.56, Room 2.7, WorkManager 2.10, Navigation 2.8
- Retrofit / OkHttp, RxJava 3, Coroutines 1.10
- Firebase BOM 33.14
- compileSdk / targetSdk **35** (Android 15)

---

## Requirements
- JDK 17
- Android SDK (compileSdk 35)
- Android Studio Ladybug or later (recommended)

---

## Setup
1. Ensure `local.properties` points to your Android SDK.
2. Open the project in Android Studio and sync Gradle.

### Firebase
Firebase is fully enabled.  To configure for a new environment:
1. Replace `app/google-services.json` with the one from your Firebase project.
2. Verify `app/build.gradle` has `id 'com.google.gms.google-services'` enabled.

---

## Build
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

### Release signing
Set in `~/.gradle/gradle.properties` or as environment variables:
```
MYAPP_UPLOAD_STORE_FILE=<path to .jks>
MYAPP_UPLOAD_STORE_PASSWORD=<password>
MYAPP_UPLOAD_KEY_ALIAS=<alias>
MYAPP_UPLOAD_KEY_PASSWORD=<password>
```

---

## Tests
```bash
./gradlew test
./gradlew connectedAndroidTest
```

---

## Backend components

### `functions/` — Firebase Cloud Functions
Handles subscription lifecycle.  Functions exported:

| Function | Type | Description |
|---|---|---|
| `checkSubscription` | HTTPS callable | Validates phone/device subscription in Firestore |
| `cleanExpiredSubs` | Scheduled (02:00 EAT) | Marks expired subscriptions |
| `onPaymentWebhook` | HTTPS request | Receives Intasend / M-Pesa payment webhooks |
| `getSubscriptionPlans` | HTTPS callable | Returns live plan catalogue (prices, durations) |
| `revokeSubscription` | HTTPS callable (admin) | Force-expires a subscription |

Deploy:
```bash
cd functions && npm install && firebase deploy --only functions
```

Set environment config before deploying:
```bash
firebase functions:config:set webhook.secret="YOUR_SECRET" admin.uid="ADMIN_UID"
```

### `cloudflare-worker/` — Cloudflare Worker
Active Lipa Na Dev billing worker. Update the Cloudflare Worker URL in `BuildConfig.BILLING_BASE_URL`.

### `firebase-webhook/`
Legacy webhook prototype kept for reference. If you use it, keep its plan mapping aligned with `cloudflare-worker/src/index.js`.

---

## Subscription plans

| Plan | Duration | Price (KES) |
|---|---|---|
| 6-Hour | 6 hours | 60 |
| 12-Hour | 12 hours | 100 |
| 24-Hour | 24 hours | 200 |
| Weekly | 7 days | 1,000 |
| **Monthly** | **30 days** | **3,000** |

Plans are defined in `cloudflare-worker/src/index.js`, mirrored in `SubscriptionActivity.kt`, and kept aligned with alternative backend copies under `functions/` and `firebase-webhook/`.

---

## Kenyan carrier support

`RateLimitManager` and `PhoneNormalizer` now use CA-Kenya 2024 number-plan prefixes:

| Carrier | Series | Rate limit |
|---|---|---|
| Safaricom | 070x, 071x, 072x, 074x–076x, 079x, 011x | 60 msgs/min |
| Airtel Kenya | 073x, 078x, 010x | 40 msgs/min |
| Telkom Kenya | 077x | 30 msgs/min |

Use `KenyaCarrierHelper.getCarrierInfo(phone)` to get carrier name, E.164 number, and rate limit in one call.

---

## Permissions
SMS, Contacts, Notifications, Foreground Service — required for bulk sending and delivery tracking.  See `app/src/main/AndroidManifest.xml`.

---

## Repo layout
```
app/             Android application
functions/       Firebase Cloud Functions (Node 18)
firebase-webhook/ Cloudflare Worker webhook handler
developer-site/  Landing page
READY_TO_INTEGRATE/ Drop-in subscription UI/layout
scripts/         Helper PowerShell scripts
docs/            Architecture notes
```

---

## Changelog

### v2 (current)
- **compileSdk / targetSdk bumped to 35** (Android 15).
- All AndroidX / Firebase / Jetpack libraries updated to latest stable releases.
- **Monthly subscription plan** added (KES 3,000 / 30 days).
- **Kenyan carrier detection** added to `RateLimitManager` — Safaricom, Airtel, Telkom prefixes from CA-KE 2024 allocation.
- `PhoneNormalizer` v2: stricter Kenyan validation, `getCarrierName()`, `isValidKenyaNumber()`.
- New `KenyaCarrierHelper` utility façade.
- `AppConfig` v2: added `Network` (HTTP timeouts) and `Cache` (TTL) sections; `FREE_RECIPIENTS_LIMIT` raised to 20.
- Firebase Functions v2: `getSubscriptionPlans`, `revokeSubscription`, grace-period support, payment-history sub-collection, duplicate-webhook detection.
- `SubscriptionActivity` v2: Monthly plan chip, price shown on chip, retry-with-back-off on return from payment browser tab.
- `SubscriptionHelper` cache TTL linked to `AppConfig.Cache.SUBSCRIPTION_STATUS_TTL_MS`.

### v1
- Initial release.
