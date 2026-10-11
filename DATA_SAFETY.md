# Data Safety — BulkSMS Manager

> **For the developer**: Complete the Play Console Data Safety form using these declarations.
> All items reflect actual code behaviour as of AppDatabase v9 / BulkSMS2 codebase.

---

## Data collected or shared

| Data type | Collected | Shared | Processed ephemerally | Required |
|---|---|---|---|---|
| SMS/MMS messages (content, metadata) | Yes — locally | No | No | Yes |
| Contact names + phone numbers | Yes — locally | No | No | Yes |
| Phone number (your number, SIM info) | Yes — locally | No | No | Yes |
| App activity (in-app settings, templates) | Yes — locally | No | No | Yes |
| Subscription / payment identifiers | Yes — sent to billing endpoint | Intasend / M-Pesa payment processor | No | Yes |
| Crash & diagnostics | Yes — Firebase Crashlytics | Google (Firebase) | No | No |
| Analytics events | Yes — Firebase Analytics | Google (Firebase) | No | No |

---

## Data handling

### Messages
- Read and written by the app as the default SMS handler.
- Stored in app-private Room database (`bulksms_database_v2`).
- Never uploaded to any server.

### Contacts
- Read-only. Used for contact pickers and recipient resolution.
- Never stored on a server.

### Phone / device identifiers
- SIM subscription ID and phone number used to:
  - Select the correct SIM for sending.
  - Look up subscription status on the billing endpoint.
- Not sold or shared with third parties.

### Billing data
- Payment is initiated via a deep-link to the Intasend / M-Pesa payment page.
- The app sends the user's phone number and a device fingerprint to the configured
  Cloudflare Worker endpoint to verify subscription status.
- Webhook secret is stored as a build-time secret and never embedded in the APK.
- Certificate pinning is applied to the billing endpoint.

### Firebase services used
- **Crashlytics** — anonymous crash reports. No PII in crash events.
- **Analytics** — aggregate usage events (no message content, no phone numbers).
- **Cloud Messaging (FCM)** — used to deliver subscription-status push notifications.
- **Remote Config** — used for feature flags.

---

## Security practices

- All network traffic uses HTTPS with TLS 1.2+.
- Certificate pinning on the billing endpoint.
- App data is stored in app-private storage protected by the Android sandbox.
- No plaintext credentials stored on device.
- Biometric lock available in settings.

---

## User controls

- Users can revoke SMS, Contacts, and Phone permissions in system settings.
- Conversations and messages can be deleted in-app.
- Opt-out requests received via inbound SMS (STOP, UNSUBSCRIBE, etc.) are automatically
  honoured and recorded; those numbers are skipped in all future bulk sends.
- Users can clear all app data via Android Settings → Apps → BulkSMS Manager → Clear data.

---

## Play Console declarations checklist

- [x] App is or requests to be the **default SMS handler**
- [x] Declare **Messages** data type: collected, on-device, not shared
- [x] Declare **Contacts** data type: collected, on-device, not shared
- [x] Declare **Phone number** data type: collected, sent to billing service
- [x] Declare **Financial info** (payment identifiers): collected, sent to payment processor
- [x] Declare **App activity**: collected, on-device
- [x] Declare **Crash logs**: collected, sent to Google (Firebase Crashlytics)
- [x] Encryption in transit: Yes
- [x] You can request data deletion: Yes (in-app + clear app data)