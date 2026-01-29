package com.bulksms.smsmanager.billing;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory class for subscription plans
 */
public class SubscriptionPlans {
    
    private static final long ONE_MONTH_MS = 30L * 24 * 60 * 60 * 1000;
    
    public static SubscriptionPlan getPlanById(String planId) {
        switch (planId) {
            case FirebaseBillingManager.PLAN_FREE:
                return createFreePlan();
            case FirebaseBillingManager.PLAN_BASIC:
                return createBasicPlan();
            case FirebaseBillingManager.PLAN_PRO:
                return createProPlan();
            case FirebaseBillingManager.PLAN_ENTERPRISE:
                return createEnterprisePlan();
            default:
                return createFreePlan();
        }
    }
    
    public static SubscriptionPlan getPlanByAmount(double amount) {
        // Match amount to plan (KES currency)
        if (amount >= 5000) {
            return createEnterprisePlan();
        } else if (amount >= 1500) {
            return createProPlan();
        } else if (amount >= 500) {
            return createBasicPlan();
        } else {
            return null; // Invalid amount for paid plans
        }
    }
    
    public static PlanLimits getLimitsForPlan(String planId) {
        switch (planId) {
            case FirebaseBillingManager.PLAN_FREE:
                return new PlanLimits(50, 500);
            case FirebaseBillingManager.PLAN_BASIC:
                return new PlanLimits(500, 10000);
            case FirebaseBillingManager.PLAN_PRO:
                return new PlanLimits(2000, 50000);
            case FirebaseBillingManager.PLAN_ENTERPRISE:
                return new PlanLimits(10000, 200000);
            default:
                return new PlanLimits(50, 500);
        }
    }
    
    private static SubscriptionPlan createFreePlan() {
        Map<String, Boolean> features = new HashMap<>();
        features.put("basic_templates", true);
        features.put("advanced_templates", false);
        features.put("analytics", false);
        features.put("priority_support", false);
        features.put("custom_sender_id", false);
        features.put("api_access", false);
        
        return new SubscriptionPlan(
            FirebaseBillingManager.PLAN_FREE,
            "Free Plan",
            0.0,
            "KES",
            Long.MAX_VALUE,
            features
        );
    }
    
    private static SubscriptionPlan createBasicPlan() {
        Map<String, Boolean> features = new HashMap<>();
        features.put("basic_templates", true);
        features.put("advanced_templates", true);
        features.put("analytics", true);
        features.put("priority_support", false);
        features.put("custom_sender_id", false);
        features.put("api_access", false);
        
        return new SubscriptionPlan(
            FirebaseBillingManager.PLAN_BASIC,
            "Basic Plan",
            500.0,
            "KES",
            ONE_MONTH_MS,
            features
        );
    }
    
    private static SubscriptionPlan createProPlan() {
        Map<String, Boolean> features = new HashMap<>();
        features.put("basic_templates", true);
        features.put("advanced_templates", true);
        features.put("analytics", true);
        features.put("priority_support", true);
        features.put("custom_sender_id", false);
        features.put("api_access", false);
        
        return new SubscriptionPlan(
            FirebaseBillingManager.PLAN_PRO,
            "Pro Plan",
            1500.0,
            "KES",
            ONE_MONTH_MS,
            features
        );
    }
    
    private static SubscriptionPlan createEnterprisePlan() {
        Map<String, Boolean> features = new HashMap<>();
        features.put("basic_templates", true);
        features.put("advanced_templates", true);
        features.put("analytics", true);
        features.put("priority_support", true);
        features.put("custom_sender_id", true);
        features.put("api_access", true);
        
        return new SubscriptionPlan(
            FirebaseBillingManager.PLAN_ENTERPRISE,
            "Enterprise Plan",
            5000.0,
            "KES",
            ONE_MONTH_MS,
            features
        );
    }
}
