const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const billing = require('./billing');

// Scheduled function for subscription reminders
exports.sendSubscriptionReminders = functions.pubsub
  .schedule('0 9 * * *') // Daily at 9 AM
  .timeZone('Africa/Nairobi')
  .onRun(async (context) => {
    return await billing.sendSubscriptionReminders();
  });

// Scheduled function for expiry checks
exports.checkExpiredSubscriptions = functions.pubsub
  .schedule('0 */6 * * *') // Every 6 hours
  .timeZone('Africa/Nairobi')
  .onRun(async (context) => {
    return await billing.checkExpiredSubscriptions();
  });

// FCM notification for renewal prompts
exports.sendRenewalNotification = functions.firestore
  .document('users/{userId}/subscriptions/{subscriptionId}')
  .onUpdate(async (change, context) => {
    return await billing.handleSubscriptionUpdate(change, context);
  });