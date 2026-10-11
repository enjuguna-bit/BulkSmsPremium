# Privacy Policy — BulkSMS Manager

**Last updated: 2026-05-08**
**Developer / Data Controller**: [Your Name / Company], Nairobi, Kenya

---

## 1. Introduction

BulkSMS Manager ("the App") is a personal Android SMS management application that lets you send bulk SMS messages, manage your inbox, and view delivery analytics — all using your device's own SIM card.

This policy explains what data the App collects, how it is used, and your rights under the **Kenya Data Protection Act 2019 (DPA)** and applicable Google Play policies.

---

## 2. Data we collect

### 2.1 Data stored locally (never leaves your device)

| Category | What | Why |
|---|---|---|
| SMS messages | Content, sender/recipient numbers, timestamps, delivery status | Core inbox and sending functionality |
| Contacts | Names and phone numbers | Recipient resolution and contact picker |
| Templates | Message templates you create | Bulk sending |
| Campaigns | Sending history, counts, delivery stats | Analytics and scheduling |
| Opt-out list | Phone numbers that have sent STOP/UNSUBSCRIBE | Compliance — to prevent re-messaging |
| App preferences | Sort order, SIM selection, theme, etc. | User experience |

All of the above is stored in an **app-private SQLite database** that is inaccessible to other apps and never uploaded to any server.

### 2.2 Data sent over the network

| Data | Sent to | Purpose |
|---|---|---|
| Your phone number (normalised) | Our billing Cloudflare Worker | Verify subscription status |
| Device fingerprint (anonymised) | Our billing Cloudflare Worker | Prevent subscription sharing |
| Payment identifiers | Intasend / M-Pesa payment page | Process subscription payment |
| Crash reports (no PII) | Google Firebase Crashlytics | Stability improvements |
| Aggregate usage events (no message content) | Google Firebase Analytics | App improvement |
| FCM registration token | Google Firebase | Push notifications |

---

## 3. How we use your data

- **Sending SMS** — your recipients' phone numbers are read from storage and passed to the Android SmsManager API. They are never uploaded.
- **Subscription verification** — your phone number is sent to our backend to check whether you have an active subscription.
- **Opt-out compliance** — inbound STOP/UNSUBSCRIBE replies are automatically recorded; those numbers are permanently excluded from future bulk sends.
- **Analytics** — aggregate, anonymised events (e.g. "campaign started", "delivery rate") help us improve the App. No message content or recipient numbers are included.

---

## 4. Legal basis (Kenya DPA 2019)

We process personal data on the following grounds:

- **Performance of a contract** — processing your phone number and device info is necessary to deliver the subscription service you have paid for.
- **Legitimate interests** — crash reporting and aggregate analytics help us maintain a reliable service.
- **Consent** — you grant SMS and Contacts permissions explicitly when prompted; you may revoke them at any time.

---

## 5. Data retention

- **On-device data** is retained until you delete it in-app or uninstall the App.
- **Subscription verification records** are retained on our backend for the duration of your subscription plus 90 days, then deleted.
- **Crash logs** are retained by Google for 90 days per Firebase Crashlytics policy.

---

## 6. Data sharing

We do **not** sell, rent, or trade your personal data.

We share data only with:
- **Google (Firebase)** — crash reporting and analytics, subject to Google's Privacy Policy.
- **Intasend / Safaricom (M-Pesa)** — payment processing, subject to their respective privacy policies.
- **Our Cloudflare Worker** — subscription verification. No data is retained beyond the API response.

---

## 7. Your rights (Kenya DPA 2019, Article 26)

You have the right to:
- **Access** the personal data we hold about you.
- **Correct** inaccurate data.
- **Delete** your data — contact us or use Android Settings → Apps → Clear Data.
- **Object** to processing based on legitimate interests.
- **Withdraw consent** by revoking permissions in Android Settings.
- **Lodge a complaint** with the Office of the Data Protection Commissioner (ODPC), Kenya.

To exercise your rights, contact us at: **[your-email@domain.com]**

---

## 8. Security

- All network communications use HTTPS with TLS 1.2+.
- Certificate pinning is applied to the billing endpoint.
- App data is encrypted by Android's app-sandbox storage model.
- Biometric lock is available as an optional setting.
- No plaintext credentials or API keys are stored on device.

---

## 9. Children

The App is not directed at children under 18. We do not knowingly collect data from minors.

---

## 10. Third-party services

| Service | Policy URL |
|---|---|
| Google Firebase | https://firebase.google.com/support/privacy |
| Intasend | https://intasend.com/privacy |
| M-Pesa / Safaricom | https://www.safaricom.co.ke/personal/safaricom-privacy-statement |

---

## 11. Changes to this policy

We will notify you of material changes via an in-app notice before they take effect.

---

## 12. Contact

**Data Controller**: [Your Name / Company]
**Email**: [your-email@domain.com]
**Location**: Nairobi, Kenya

For complaints: Office of the Data Protection Commissioner — https://www.odpc.go.ke