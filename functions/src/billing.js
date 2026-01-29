const admin = require('firebase-admin');
const firestore = admin.firestore();
const messaging = admin.messaging();

const COLLECTION_USERS = 'users';
const COLLECTION_SUBSCRIPTIONS = 'subscriptions';
const STATUS_ACTIVE = 'active';
const STATUS_EXPIRED = 'expired';
const STATUS_CANCELLED = 'cancelled';

/**
 * Send subscription reminders to users with expiring subscriptions
 */
async function sendSubscriptionReminders() {
  const now = Date.now();
  const reminderTime = now + (3 * 24 * 60 * 60 * 1000); // 3 days from now
  
  try {
    const expiringSubscriptions = await firestore
      .collectionGroup(COLLECTION_SUBSCRIPTIONS)
      .where('status', '==', STATUS_ACTIVE)
      .where('end_date', '<=', reminderTime)
      .where('end_date', '>', now)
      .where('auto_renew', '==', false)
      .get();
    
    const notifications = [];
    
    for (const doc of expiringSubscriptions.docs) {
      const subscription = doc.data();
      const userId = doc.ref.parent.parent.id;
      
      // Get user notification tokens
      const userDoc = await firestore.collection(COLLECTION_USERS).doc(userId).get();
      const tokens = userDoc.data()?.fcmTokens || [];
      
      if (tokens.length > 0) {
        const daysLeft = Math.ceil((subscription.end_date - now) / (24 * 60 * 60 * 1000));
        
        notifications.push({
          tokens: tokens,
          notification: {
            title: 'Subscription Expiring Soon',
            body: `Your ${subscription.plan_id} plan expires in ${daysLeft} days. Renew now to continue service.`
          },
          data: {
            type: 'subscription_reminder',
            planId: subscription.plan_id,
            daysLeft: daysLeft.toString()
          }
        });
      }
    }
    
    // Send batch notifications
    const results = await Promise.allSettled(
      notifications.map(notification => 
        messaging.sendEachForMulticast(notification)
      )
    );
    
    console.log(`Sent ${results.length} reminder notifications`);
    return { success: true, sent: results.length };
    
  } catch (error) {
    console.error('Error sending reminders:', error);
    throw error;
  }
}

/**
 * Check for expired subscriptions and update status
 */
async function checkExpiredSubscriptions() {
  const now = Date.now();
  
  try {
    const expiredSubscriptions = await firestore
      .collectionGroup(COLLECTION_SUBSCRIPTIONS)
      .where('status', '==', STATUS_ACTIVE)
      .where('end_date', '<=', now)
      .get();
    
    const updates = [];
    
    for (const doc of expiredSubscriptions.docs) {
      const userId = doc.ref.parent.parent.id;
      
      updates.push(
        doc.ref.update({
          status: STATUS_EXPIRED,
          expired_at: now,
          updated_at: now
        })
      );
      
      // Update user plan to free
      updates.push(
        firestore.collection(COLLECTION_USERS).doc(userId).update({
          current_plan: 'free',
          plan_updated_at: now
        })
      );
    }
    
    await Promise.all(updates);
    console.log(`Expired ${updates.length / 2} subscriptions`);
    return { success: true, expired: updates.length / 2 };
    
  } catch (error) {
    console.error('Error checking expired subscriptions:', error);
    throw error;
  }
}

/**
 * Handle subscription updates and send renewal notifications
 */
async function handleSubscriptionUpdate(change, context) {
  const before = change.before.data();
  const after = change.after.data();
  
  // If subscription was just cancelled, send cancellation confirmation
  if (before.status === STATUS_ACTIVE && after.status === STATUS_CANCELLED) {
    const userId = context.params.userId;
    
    const userDoc = await firestore.collection(COLLECTION_USERS).doc(userId).get();
    const tokens = userDoc.data()?.fcmTokens || [];
    
    if (tokens.length > 0) {
      await messaging.sendEachForMulticast({
        tokens: tokens,
        notification: {
          title: 'Subscription Cancelled',
          body: 'Your subscription has been cancelled. You can renew anytime.'
        },
        data: {
          type: 'subscription_cancelled',
          planId: after.plan_id
        }
      });
    }
  }
  
  return { success: true };
}

module.exports = {
  sendSubscriptionReminders,
  checkExpiredSubscriptions,
  handleSubscriptionUpdate
};