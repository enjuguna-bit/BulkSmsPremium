package com.afriserve.smsmanager;

/**
 * Application Configuration Constants — v3
 *
 * Single source of truth for every tunable number used across the app,
 * workers, and services. Grouped by concern so callers import only what
 * they need (e.g. {@code AppConfig.Worker.MAX_RETRY_COUNT}).
 *
 * Changelog v3:
 *  - Added {@link Scheduler} class for scheduled-campaign timing constants.
 *  - Added {@link Analytics} class for event-name constants and session params.
 *  - Added {@link Database} class for Room migration / schema version tracking.
 *  - Bumped {@link Limits#FREE_RECIPIENTS_LIMIT} comment to clarify it is
 *    enforced in BulkSmsService (CSV path) and BulkSmsViewModel (manual path).
 *
 * Changelog v2:
 *  - Added {@link Network} class for HTTP timeouts and retry settings.
 *  - Added {@link Cache} class for local-cache TTL values.
 *  - Raised {@link Limits#FREE_RECIPIENTS_LIMIT} to 20 (better trial UX).
 *  - Added {@link Limits#MAX_MESSAGE_LENGTH} (160 chars / SMS unit).
 *  - Added {@link Limits#MAX_MULTIPART_PARTS} guard for multipart messages.
 *  - Extended {@link Worker} with {@code PROGRESS_NOTIFICATION_UPDATE_MS}.
 *  - Extended {@link Service} with {@code MAX_CONCURRENT_SENDS}.
 */
public final class AppConfig {

    private AppConfig() { /* static constants only */ }

    // ── Default user-facing settings ─────────────────────────────────────────
    public static final class Defaults {
        public static final String  THEME_MODE            = "system";
        public static final boolean NOTIFICATIONS_ENABLED = true;
        /** Default send-speed in msgs/hour. Keep in sync with BulkSmsViewModel init. */
        public static final int     SEND_SPEED            = 300;
        public static final int     SIM_SLOT              = 0;
        public static final String  MODE                  = "excel";
    }

    // ── Permission request codes ──────────────────────────────────────────────
    public static final class Permissions {
        public static final int PERMISSION_REQUEST_CODE  = 1001;
        public static final int DEFAULT_SMS_REQUEST_CODE = 101;
    }

    // ── Business / UI limits ──────────────────────────────────────────────────
    public static final class Limits {
        public static final int MAX_RECENT_TEMPLATES     = 10;
        public static final int MAX_RECIPIENTS_PER_BATCH = 100;
        /**
         * Free-tier CSV recipient cap before the subscription gate applies.
         * Enforced in BulkSmsService (CSV import path) and in BulkSmsViewModel
         * (manual-entry path).
         */
        public static final int FREE_RECIPIENTS_LIMIT    = 20;
        public static final int MIN_SEND_SPEED           = 60;
        public static final int MAX_SEND_SPEED           = 3_000;
        /** Characters in a single GSM-7 SMS unit (plain text). */
        public static final int MAX_MESSAGE_LENGTH       = 160;
        /** Maximum number of SMS parts before the app warns the user. */
        public static final int MAX_MULTIPART_PARTS      = 3;
        /** Maximum number of concurrent scheduled campaigns in SCHEDULED state. */
        public static final int MAX_SCHEDULED_CAMPAIGNS  = 20;
    }

    // ── Background polling intervals (milliseconds) ───────────────────────────
    public static final class Polling {
        /** How often BulkSmsViewModel polls delivery / queue / rate-limit stats. */
        public static final long DELIVERY_STATS_INTERVAL_MS    = 3_000L;
        /** Minimum wall-clock time between Dashboard auto-refreshes on resume. */
        public static final long DASHBOARD_REFRESH_THROTTLE_MS = 5_000L;
        /** Session persist debounce when writing template changes. */
        public static final long TEMPLATE_SAVE_DEBOUNCE_MS     = 600L;
    }

    // ── WorkManager retry / back-off ──────────────────────────────────────────
    public static final class Worker {
        /** Initial back-off before the first WorkManager retry (ms). */
        public static final long   INITIAL_BACKOFF_MS                = 10_000L;
        /** Maximum number of automatic WorkManager retries before giving up. */
        public static final int    MAX_RETRY_COUNT                   = 3;
        /** Foreground-service notification ID used by BulkSmsSendingWorker. */
        public static final int    NOTIFICATION_ID                   = 2001;
        public static final String CHANNEL_ID                        = "bulk_sms_sending_channel";
        /** Minimum interval between foreground-notification progress updates (ms). */
        public static final long   PROGRESS_NOTIFICATION_UPDATE_MS  = 1_500L;
    }

    // ── BackgroundSmsSenderService ────────────────────────────────────────────
    public static final class Service {
        /** Hard timeout for BackgroundSmsSenderService before it self-stops (ms). */
        public static final long SERVICE_TIMEOUT_MS        = 300_000L; // 5 min
        public static final int  FOREGROUND_NOTIFICATION_ID = 1;
        /** Maximum number of messages that may be in-flight simultaneously. */
        public static final int  MAX_CONCURRENT_SENDS      = 1; // serial; raise with caution

        /** How often BulkSmsService persists the in-progress session to disk (ms). */
        public static final long SESSION_PERSIST_INTERVAL_MS      = 1_500L;

        /** How often BulkSmsService re-reads the persisted session to pick up
         *  pause / stop / speed-change signals from the UI thread (ms). */
        public static final long SESSION_FLAG_REFRESH_INTERVAL_MS = 1_000L;

        /** Sleep granularity inside the rate-limit delay loop (ms).
         *  Smaller = more responsive to stop/pause, more CPU wake-ups. */
        public static final long CONTROL_CHECK_INTERVAL_MS        = 250L;

        /** Max recipients for free-tier CSV sends. */
        public static final int  CSV_FREE_LIMIT                   = 15;
    }

    // ── HTTP / network settings ───────────────────────────────────────────────
    public static final class Network {
        /** Socket / connect timeout for billing and webhook calls (ms). */
        public static final long CONNECT_TIMEOUT_MS = 15_000L;
        public static final long READ_TIMEOUT_MS    = 20_000L;
        public static final long WRITE_TIMEOUT_MS   = 15_000L;
        /** How many times the app retries a failed HTTP request before surfacing an error. */
        public static final int  MAX_HTTP_RETRIES   = 2;
    }

    // ── Local cache TTLs ──────────────────────────────────────────────────────
    public static final class Cache {
        /** How long a cached subscription-status response stays valid (ms). */
        public static final long SUBSCRIPTION_STATUS_TTL_MS = 5 * 60_000L; // 5 min
        /** How long dashboard stats are considered fresh before a reload is forced (ms). */
        public static final long DASHBOARD_STATS_TTL_MS     = 60_000L;     // 1 min
    }

    // ── Scheduled campaign constants ──────────────────────────────────────────
    /**
     * Constants used by {@link com.afriserve.smsmanager.workers.ScheduledSmsWorker}
     * and the scheduling UI.
     */
    public static final class Scheduler {
        /**
         * How far in the future the earliest allowed scheduled time can be (ms).
         * Prevents scheduling in the past due to clock skew or UI lag.
         */
        public static final long MIN_SCHEDULE_LEAD_TIME_MS = 60_000L;       // 1 min

        /**
         * How far ahead the user may schedule a campaign (ms).
         * Campaigns scheduled beyond this window are rejected.
         */
        public static final long MAX_SCHEDULE_LOOKAHEAD_MS = 365L * 24 * 60 * 60 * 1000; // 1 year

        /**
         * How frequently ScheduledSmsWorker runs to check for due campaigns (ms).
         * WorkManager minimum periodic interval is 15 minutes; keep this aligned.
         */
        public static final long POLL_INTERVAL_MS          = 15 * 60_000L;  // 15 min

        /**
         * Tolerance window: a campaign whose nextExecutionTime is within this
         * many milliseconds in the past is still considered due and will be executed.
         */
        public static final long EXECUTION_TOLERANCE_MS    = 5 * 60_000L;   // 5 min

        /** WorkManager unique work name for the periodic scheduler poller. */
        public static final String UNIQUE_WORK_NAME        = "scheduled_campaign_poller";
    }

    // ── Analytics event names ─────────────────────────────────────────────────
    /**
     * String constants for Firebase/local analytics events.
     * Using constants avoids typos in event names across fragments and viewmodels.
     */
    public static final class Analytics {
        // Campaign lifecycle
        public static final String EVENT_CAMPAIGN_STARTED   = "campaign_started";
        public static final String EVENT_CAMPAIGN_PAUSED    = "campaign_paused";
        public static final String EVENT_CAMPAIGN_RESUMED   = "campaign_resumed";
        public static final String EVENT_CAMPAIGN_STOPPED   = "campaign_stopped";
        public static final String EVENT_CAMPAIGN_COMPLETED = "campaign_completed";
        public static final String EVENT_CAMPAIGN_SCHEDULED = "campaign_scheduled";

        // File import
        public static final String EVENT_FILE_IMPORTED      = "file_imported";
        public static final String EVENT_FILE_IMPORT_FAILED = "file_import_failed";

        // Template usage
        public static final String EVENT_TEMPLATE_USED      = "template_used";
        public static final String EVENT_TEMPLATE_CREATED   = "template_created";
        public static final String EVENT_TEMPLATE_DELETED   = "template_deleted";

        // Billing
        public static final String EVENT_SUBSCRIPTION_STARTED  = "subscription_started";
        public static final String EVENT_SUBSCRIPTION_UPGRADED = "subscription_upgraded";
        public static final String EVENT_FREE_LIMIT_HIT        = "free_limit_reached";

        // Parameters
        public static final String PARAM_RECIPIENT_COUNT  = "recipient_count";
        public static final String PARAM_SENT_COUNT       = "sent_count";
        public static final String PARAM_FAILED_COUNT     = "failed_count";
        public static final String PARAM_CAMPAIGN_ID      = "campaign_id";
        public static final String PARAM_TEMPLATE_ID      = "template_id";
        public static final String PARAM_FILE_TYPE        = "file_type";     // csv / excel
        public static final String PARAM_SIM_SLOT         = "sim_slot";
        public static final String PARAM_SEND_SPEED       = "send_speed";
        public static final String PARAM_CARRIER          = "carrier";
    }

    // ── Room database versioning ──────────────────────────────────────────────
    /**
     * Centralising the Room schema version here means a single grep finds every
     * place that needs updating when a migration is added.
     */
    public static final class Database {
        /**
         * Current Room schema version.
         * Increment this and add a corresponding Migration in AppDatabase whenever
         * any @Entity schema changes (new columns, new tables, index changes).
         *
         * History:
         *  v1 → v2 : Added charCount, messagePartCount to campaign_entities.
         *             Added charCount, isArchived to template_entities.
         */
        public static final int SCHEMA_VERSION = 2;
    }
}
