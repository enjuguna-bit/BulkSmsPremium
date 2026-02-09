package com.afriserve.smsmanager;

/**
 * Application Configuration Constants
 */
public class AppConfig {
    
    public static class Defaults {
        public static final String THEME_MODE = "system";
        public static final boolean NOTIFICATIONS_ENABLED = true;
        public static final int SEND_SPEED = 400;
        public static final int SIM_SLOT = 0;
        public static final String MODE = "excel";
    }
    
    public static class Permissions {
        public static final int PERMISSION_REQUEST_CODE = 1001;
        public static final int DEFAULT_SMS_REQUEST_CODE = 101;
    }
    
    public static class Limits {
        public static final int MAX_RECENT_TEMPLATES = 10;
        public static final int MAX_RECIPIENTS_PER_BATCH = 100;
        public static final int MIN_SEND_SPEED = 100;
        public static final int MAX_SEND_SPEED = 2000;
    }
}
