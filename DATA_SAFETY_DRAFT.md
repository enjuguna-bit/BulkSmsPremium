# Data Safety Draft (Play Console)

This is a draft based on the current codebase. Review and adjust to match actual behavior,
backends, and data flows before submitting.

## Data Types Accessed
- Messages: SMS/MMS content and metadata (sender/recipient numbers, timestamps, delivery status).
- Contacts: names and phone numbers for contact pickers and search.
- Phone/Device: SIM subscription info for multi-SIM selection, network state.
- Files: CSV/Excel content selected by the user for bulk sending.
- App activity: in-app settings, message templates, local message history.

## Collection And Sharing
- On-device processing: SMS/MMS and contacts are accessed and stored locally to power inbox,
  history, and sending flows.
- Network: subscription status checks via the configured endpoint. Firebase SDKs are present;
  enable only the services you actually use.
- Data is not sold. Share only with service providers you enable and document.

## Security Practices
- Data stored in app-private storage, protected by the Android sandbox.
- Use HTTPS for any network requests and verify certificates.

## User Controls
- Users can revoke permissions in system settings.
- Provide a way to clear local app data or delete conversations if available.

## Play Console Declarations
- Declare Default SMS handler functionality.
- Provide permission justifications for Messages and Contacts.
- Provide a privacy policy URL.
