package com.bulksms.smsmanager.billing;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.bulksms.smsmanager.auth.SecureStorageEnhanced;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Enhanced Firebase-Based Billing Manager
 */
@Singleton
public class FirebaseBillingManager {
    private static final String TAG = "FirebaseBilling";
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_SUBSCRIPTIONS = "subscriptions";
    private static final String COLLECTION_BILLING = "billing_records";
    private static final String COLLECTION_USAGE = "usage_tracking";
    
    // Plans
    public static final String PLAN_FREE = "free";
    public static final String PLAN_BASIC = "basic"; 
    public static final String PLAN_PRO = "pro";
    public static final String PLAN_ENTERPRISE = "enterprise";
    
    // Status
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_PENDING = "pending";
    
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final SecureStorageEnhanced secureStorage;

    @Inject
    public FirebaseBillingManager(SecureStorageEnhanced secureStorage) {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.secureStorage = secureStorage;
    }
    
    /**
     * Get current active subscription
     */
    public CompletableFuture<SubscriptionInfo> getCurrentSubscription() {
        CompletableFuture<SubscriptionInfo> future = new CompletableFuture<>();
        
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                future.complete(null);
                return future;
            }
            
            CollectionReference subscriptionsRef = firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SUBSCRIPTIONS);
                
            subscriptionsRef
                .whereEqualTo("status", STATUS_ACTIVE)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.getDocuments().size() > 0) {
                        DocumentSnapshot doc = snapshot.getDocuments().get(0);
                        future.complete(documentToSubscription(doc));
                    } else {
                        createFreeSubscription()
                            .thenAccept(future::complete)
                            .exceptionally(throwable -> {
                                future.completeExceptionally(throwable);
                                return null;
                            });
                    }
                })
                .addOnFailureListener(future::completeExceptionally);
                
        } catch (Exception e) {
            Log.e(TAG, "Failed to get subscription", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Create subscription from payment
     */
    public CompletableFuture<String> createSubscription(
        String planId,
        double amount,
        String paymentMethod,
        String transactionId,
        boolean autoRenew
    ) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                future.completeExceptionally(new Exception("Not authenticated"));
                return future;
            }
            
            SubscriptionPlan planDetails = getPlanDetails(planId);
            
            // Cancel existing subscriptions
            cancelActiveSubscriptions(userId)
                .thenCompose(aVoid -> {
                    // Create new subscription
                    Map<String, Object> subscription = new HashMap<>();
                    subscription.put("plan_id", planId);
                    subscription.put("status", STATUS_ACTIVE);
                    subscription.put("start_date", System.currentTimeMillis());
                    subscription.put("end_date", System.currentTimeMillis() + planDetails.getDurationMs());
                    subscription.put("auto_renew", autoRenew);
                    subscription.put("amount", amount);
                    subscription.put("currency", planDetails.getCurrency());
                    subscription.put("payment_method", paymentMethod);
                    subscription.put("transaction_id", transactionId);
                    subscription.put("features", planDetails.getFeatures());
                    subscription.put("created_at", System.currentTimeMillis());
                    subscription.put("updated_at", System.currentTimeMillis());
                    
                    // Convert Firebase Task to CompletableFuture
                    CompletableFuture<DocumentReference> docRefFuture = new CompletableFuture<>();
                    firestore
                        .collection(COLLECTION_USERS)
                        .document(userId)
                        .collection(COLLECTION_SUBSCRIPTIONS)
                        .add(subscription)
                        .addOnSuccessListener(docRef -> docRefFuture.complete(docRef))
                        .addOnFailureListener(docRefFuture::completeExceptionally);
                    return docRefFuture;
                })
                .thenCompose(docRef -> {
                    // Update user's plan info
                    return updateUserPlan(userId, planId)
                        .thenCompose(aVoid -> {
                            // Record billing
                            return recordBillingEvent(
                                "subscription_created",
                                planId,
                                amount,
                                paymentMethod,
                                transactionId
                            ).thenApply(voidResult -> docRef);
                        });
                })
                .thenAccept(docRef -> future.complete(docRef.getId()))
                .exceptionally(throwable -> {
                    future.completeExceptionally(throwable);
                    return null;
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Subscription creation failed", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Cancel a subscription and process refund
     */
    public CompletableFuture<Void> cancelSubscription(String subscriptionId, String reason) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                future.completeExceptionally(new Exception("Not authenticated"));
                return future;
            }
            
            DocumentReference subscriptionRef = firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SUBSCRIPTIONS)
                .document(subscriptionId);
                
            subscriptionRef.get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        future.completeExceptionally(new Exception("Subscription not found"));
                        return;
                    }
                    
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", STATUS_CANCELLED);
                    updates.put("cancelled_at", System.currentTimeMillis());
                    updates.put("cancel_reason", reason);
                    updates.put("updated_at", System.currentTimeMillis());
                    
                    // Update subscription status
                    subscriptionRef.update(updates)
                        .addOnSuccessListener(aVoid -> {
                            // Process refund if applicable
                            processRefund(document.getData())
                                .thenCompose(refundResult -> {
                                    // Update user plan to free
                                    return updateUserPlan(userId, PLAN_FREE);
                                })
                                .thenCompose(aVoid2 -> {
                                    // Record cancellation event
                                    return recordBillingEvent(
                                        "subscription_cancelled",
                                        document.getString("plan_id"),
                                        document.getDouble("amount"),
                                        document.getString("payment_method"),
                                        document.getString("transaction_id")
                                    );
                                })
                                .thenAccept(aVoid3 -> future.complete(null))
                                .exceptionally(throwable -> {
                                    Log.e(TAG, "Failed to process cancellation", throwable);
                                    future.completeExceptionally(throwable);
                                    return null;
                                });
                        })
                        .addOnFailureListener(future::completeExceptionally);
                })
                .addOnFailureListener(future::completeExceptionally);
                
        } catch (Exception e) {
            Log.e(TAG, "Cancellation failed", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Process refund for cancelled subscription
     */
    private CompletableFuture<Void> processRefund(Map<String, Object> subscriptionData) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // Calculate refund amount (prorated)
            Double amount = (Double) subscriptionData.get("amount");
            Long startDate = (Long) subscriptionData.get("start_date");
            Long endDate = (Long) subscriptionData.get("end_date");
            
            if (amount != null && startDate != null && endDate != null) {
                long totalDuration = endDate - startDate;
                long usedDuration = System.currentTimeMillis() - startDate;
                double refundAmount = amount * (1.0 - (double) usedDuration / totalDuration);
                
                // For M-Pesa, refunds are typically manual
                // In production, integrate with payment processor API
                Log.i(TAG, "Refund calculated: " + refundAmount + " for subscription");
                
                // Record refund event
                recordBillingEvent(
                    "refund_processed",
                    (String) subscriptionData.get("plan_id"),
                    refundAmount,
                    (String) subscriptionData.get("payment_method"),
                    (String) subscriptionData.get("transaction_id")
                ).thenAccept(aVoid -> future.complete(null))
                 .exceptionally(throwable -> {
                     Log.w(TAG, "Failed to record refund", throwable);
                     future.complete(null); // Don't fail cancellation for refund recording
                     return null;
                 });
            } else {
                future.complete(null);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Refund processing failed", e);
            future.complete(null); // Don't fail cancellation
        }
        
        return future;
    }
    
    /**
     * Track SMS usage
     */
    public CompletableFuture<Void> trackSmsUsage(int count) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                future.completeExceptionally(new Exception("Not authenticated"));
                return future;
            }
            
            getCurrentSubscription()
                .thenCompose(subscription -> {
                    if (subscription == null) {
                        throw new RuntimeException("No subscription");
                    }
                    
                    // Check limits
                    return getCurrentUsage(userId)
                        .thenCompose(usage -> {
                            PlanLimits limits = getPlanLimits(subscription.getPlanId());
                            
                            if (usage.getDailySms() + count > limits.getDailySms()) {
                                throw new RuntimeException("Daily limit exceeded");
                            }
                            
                            if (usage.getMonthlySms() + count > limits.getMonthlySms()) {
                                throw new RuntimeException("Monthly limit exceeded");
                            }
                            
                            // Record usage
                            Map<String, Object> usageRecord = new HashMap<>();
                            usageRecord.put("user_id", userId);
                            usageRecord.put("type", "sms");
                            usageRecord.put("count", count);
                            usageRecord.put("timestamp", System.currentTimeMillis());
                            usageRecord.put("date", getCurrentDateKey());
                            usageRecord.put("month", getCurrentMonthKey());
                            
                            // Convert Firebase Task to CompletableFuture
                            CompletableFuture<DocumentReference> usageFuture = new CompletableFuture<>();
                            firestore.collection(COLLECTION_USAGE).add(usageRecord)
                                .addOnSuccessListener(usageFuture::complete)
                                .addOnFailureListener(usageFuture::completeExceptionally);
                            return usageFuture;
                        });
                })
                .thenAccept(documentReference -> future.complete(null))
                .exceptionally(throwable -> {
                    Log.e(TAG, "Usage tracking failed", throwable);
                    future.completeExceptionally(throwable);
                    return null;
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Usage tracking failed", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Get usage stats
     */
    public CompletableFuture<UsageStats> getUsageStats() {
        CompletableFuture<UsageStats> future = new CompletableFuture<>();
        
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                future.complete(new UsageStats());
                return future;
            }
            
            String dateKey = getCurrentDateKey();
            String monthKey = getCurrentMonthKey();
            
            // Get daily usage
            CompletableFuture<QuerySnapshot> dailyQuery = new CompletableFuture<>();
            firestore
                .collection(COLLECTION_USAGE)
                .whereEqualTo("user_id", userId)
                .whereEqualTo("date", dateKey)
                .get()
                .addOnSuccessListener(dailyQuery::complete)
                .addOnFailureListener(dailyQuery::completeExceptionally);
                
            // Get monthly usage
            CompletableFuture<QuerySnapshot> monthlyQuery = new CompletableFuture<>();
            firestore
                .collection(COLLECTION_USAGE)
                .whereEqualTo("user_id", userId)
                .whereEqualTo("month", monthKey)
                .get()
                .addOnSuccessListener(monthlyQuery::complete)
                .addOnFailureListener(monthlyQuery::completeExceptionally);
                
            dailyQuery.thenCompose(dailySnapshot -> 
                monthlyQuery.thenApply(monthlySnapshot -> {
                    int dailyTotal = 0;
                    for (DocumentSnapshot doc : dailySnapshot.getDocuments()) {
                        Long count = doc.getLong("count");
                        if (count != null) {
                            dailyTotal += count.intValue();
                        }
                    }
                    
                    int monthlyTotal = 0;
                    for (DocumentSnapshot doc : monthlySnapshot.getDocuments()) {
                        Long count = doc.getLong("count");
                        if (count != null) {
                            monthlyTotal += count.intValue();
                        }
                    }
                    
                    return new UsageStats(dailyTotal, monthlyTotal);
                })
            ).thenAccept(future::complete)
             .exceptionally(throwable -> {
                 Log.e(TAG, "Usage stats failed", throwable);
                 future.complete(new UsageStats());
                 return null;
             });
            
        } catch (Exception e) {
            Log.e(TAG, "Usage stats failed", e);
            future.complete(new UsageStats());
        }
        
        return future;
    }
    
    /**
     * Validate M-Pesa transaction
     */
    public CompletableFuture<SubscriptionPlan> validateMpesaTransaction(
        double amount,
        String transactionId
    ) {
        CompletableFuture<SubscriptionPlan> future = new CompletableFuture<>();
        
        try {
            // Verify transaction hasn't been used
            isTransactionUsed(transactionId)
                .thenCompose(used -> {
                    if (used) {
                        throw new RuntimeException("Transaction already processed");
                    }
                    
                    // Match amount to plan
                    SubscriptionPlan plan = SubscriptionPlans.getPlanByAmount(amount);
                    if (plan == null) {
                        throw new RuntimeException("Invalid amount");
                    }
                    
                    future.complete(plan);
                    return CompletableFuture.completedFuture(null);
                })
                .exceptionally(throwable -> {
                    future.completeExceptionally(throwable);
                    return null;
                });
                
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    // Private helpers
    private CompletableFuture<Boolean> isTransactionUsed(String transactionId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        firestore
            .collection(COLLECTION_BILLING)
            .whereEqualTo("transaction_id", transactionId)
            .get()
            .addOnSuccessListener(snapshot -> 
                future.complete(snapshot.getDocuments().size() > 0)
            )
            .addOnFailureListener(future::completeExceptionally);
            
        return future;
    }
    
    private CompletableFuture<Void> cancelActiveSubscriptions(String userId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        firestore
            .collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_SUBSCRIPTIONS)
            .whereEqualTo("status", STATUS_ACTIVE)
            .get()
            .addOnSuccessListener(snapshot -> {
                CompletableFuture<?>[] futures = new CompletableFuture[snapshot.getDocuments().size()];
                
                for (int i = 0; i < snapshot.getDocuments().size(); i++) {
                    DocumentSnapshot doc = snapshot.getDocuments().get(i);
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", STATUS_CANCELLED);
                    updates.put("cancelled_at", System.currentTimeMillis());
                    updates.put("updated_at", System.currentTimeMillis());
                    
                    final int index = i; // Create effectively final copy for lambda
                    futures[i] = new CompletableFuture<>();
                    doc.getReference().update(updates)
                        .addOnSuccessListener(aVoid -> futures[index].complete(null))
                        .addOnFailureListener(futures[index]::completeExceptionally);
                }
                
                CompletableFuture.allOf(futures)
                    .thenAccept(aVoid -> future.complete(null))
                    .exceptionally(throwable -> {
                        Log.w(TAG, "Failed to cancel some subscriptions", throwable);
                        future.complete(null); // Don't fail the whole operation
                        return null;
                    });
            })
            .addOnFailureListener(future::completeExceptionally);
            
        return future;
    }
    
    private CompletableFuture<Void> updateUserPlan(String userId, String planId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("current_plan", planId);
        updates.put("plan_updated_at", System.currentTimeMillis());
        
        firestore
            .collection(COLLECTION_USERS)
            .document(userId)
            .update(updates)
            .addOnSuccessListener(aVoid -> future.complete(null))
            .addOnFailureListener(future::completeExceptionally);
            
        return future;
    }
    
    private CompletableFuture<Void> recordBillingEvent(
        String type,
        String planId,
        Double amount,
        String paymentMethod,
        String transactionId
    ) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                future.complete(null);
                return future;
            }
            
            Map<String, Object> billingRecord = new HashMap<>();
            billingRecord.put("user_id", userId);
            billingRecord.put("type", type);
            billingRecord.put("plan_id", planId);
            billingRecord.put("amount", amount);
            billingRecord.put("currency", "KES");
            billingRecord.put("payment_method", paymentMethod);
            billingRecord.put("transaction_id", transactionId);
            billingRecord.put("timestamp", System.currentTimeMillis());
            billingRecord.put("status", "completed");
            
            firestore.collection(COLLECTION_BILLING)
                .add(billingRecord)
                .addOnSuccessListener(aVoid -> future.complete(null))
                .addOnFailureListener(throwable -> {
                    Log.w(TAG, "Failed to record billing", throwable);
                    future.complete(null); // Don't fail the whole operation
                });
                
        } catch (Exception e) {
            Log.w(TAG, "Failed to record billing", e);
            future.complete(null);
        }
        
        return future;
    }
    
    private SubscriptionInfo documentToSubscription(DocumentSnapshot document) {
        Map<String, Boolean> features = (Map<String, Boolean>) document.get("features");
        if (features == null) {
            features = new HashMap<>();
        }
        
        return new SubscriptionInfo(
            document.getId(),
            document.getString("plan_id") != null ? document.getString("plan_id") : PLAN_FREE,
            document.getString("status") != null ? document.getString("status") : STATUS_EXPIRED,
            document.getLong("start_date") != null ? document.getLong("start_date") : 0L,
            document.getLong("end_date") != null ? document.getLong("end_date") : 0L,
            document.getBoolean("auto_renew") != null ? document.getBoolean("auto_renew") : false,
            document.getDouble("amount") != null ? document.getDouble("amount") : 0.0,
            document.getString("currency") != null ? document.getString("currency") : "KES",
            features,
            document.getLong("created_at") != null ? document.getLong("created_at") : 0L
        );
    }
    
    private CompletableFuture<SubscriptionInfo> createFreeSubscription() {
        CompletableFuture<SubscriptionInfo> future = new CompletableFuture<>();
        
        try {
            String userId = getCurrentUserId();
            if (userId == null) {
                future.completeExceptionally(new Exception("Not authenticated"));
                return future;
            }
            
            SubscriptionPlan planDetails = getPlanDetails(PLAN_FREE);
            
            Map<String, Object> subscription = new HashMap<>();
            subscription.put("plan_id", PLAN_FREE);
            subscription.put("status", STATUS_ACTIVE);
            subscription.put("start_date", System.currentTimeMillis());
            subscription.put("end_date", Long.MAX_VALUE);
            subscription.put("auto_renew", false);
            subscription.put("amount", planDetails.getPrice());
            subscription.put("currency", planDetails.getCurrency());
            subscription.put("features", planDetails.getFeatures());
            subscription.put("created_at", System.currentTimeMillis());
            subscription.put("updated_at", System.currentTimeMillis());
            
            firestore
                .collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_SUBSCRIPTIONS)
                .add(subscription)
                .addOnSuccessListener(docRef -> {
                    SubscriptionInfo info = new SubscriptionInfo(
                        docRef.getId(),
                        PLAN_FREE,
                        STATUS_ACTIVE,
                        System.currentTimeMillis(),
                        Long.MAX_VALUE,
                        false,
                        planDetails.getPrice(),
                        planDetails.getCurrency(),
                        planDetails.getFeatures(),
                        System.currentTimeMillis()
                    );
                    future.complete(info);
                })
                .addOnFailureListener(future::completeExceptionally);
                
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    private CompletableFuture<UsageStats> getCurrentUsage(String userId) {
        // This would be similar to getUsageStats but for current user
        return getUsageStats();
    }
    
    private String getCurrentUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
    
    private String getCurrentDateKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
    
    private String getCurrentMonthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }
    
    // Helper methods for plan details
    private SubscriptionPlan getPlanDetails(String planId) {
        return SubscriptionPlans.getPlanById(planId);
    }
    
    private PlanLimits getPlanLimits(String planId) {
        return SubscriptionPlans.getLimitsForPlan(planId);
    }
}
