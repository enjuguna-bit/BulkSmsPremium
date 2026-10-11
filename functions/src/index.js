'use strict';
/**
 * Firebase Cloud Functions — BulkSMS2 backend  v2
 *
 * Functions:
 *  1. checkSubscription   HTTPS callable — validates phone/device subscription
 *  2. cleanExpiredSubs    Scheduled      — daily cleanup at 02:00 Africa/Nairobi
 *  3. onPaymentWebhook    HTTPS endpoint — receives Intasend / M-Pesa webhooks
 *  4. getSubscriptionPlans HTTPS callable — returns current plan definitions
 *  5. revokeSubscription  HTTPS callable — admin-only forced revocation
 *
 * v2 changes:
 *  - Added monthly plan (KES 3,000 / 30 days).
 *  - Grace period: subscriptions within 2 h of expiry are still treated as
 *    active so users aren't cut off mid-campaign.
 *  - Plan definitions exposed via getSubscriptionPlans so the Android app
 *    never hard-codes prices.
 *  - onPaymentWebhook now records a payment_history sub-collection entry for
 *    every processed webhook — useful for auditing and duplicate detection.
 *  - revokeSubscription allows a verified admin UID to force-expire any sub.
 *  - All functions use structured logging (functions.logger.*).
 *
 * Environment config:
 *   firebase functions:config:set webhook.secret="YOUR_SECRET" admin.uid="UID"
 */

const functions = require('firebase-functions');
const admin     = require('firebase-admin');
const crypto    = require('crypto');

admin.initializeApp();
const db = admin.firestore();

// ── Constants ─────────────────────────────────────────────────────────────────
const GRACE_PERIOD_MS = 2 * 60 * 60 * 1000; // 2 hours grace after expiry

/** Plan catalogue — single source of truth shared with the Android client. */
const PLANS = {
  six_hour:    { label: '6-Hour',   durationMs: 6 * 60 * 60 * 1000,       amount: 60   },
  twelve_hour: { label: '12-Hour',  durationMs: 12 * 60 * 60 * 1000,      amount: 100  },
  daily:       { label: '24-Hour',  durationMs: 24 * 60 * 60 * 1000,      amount: 200  },
  weekly:      { label: 'Weekly',   durationMs: 7 * 24 * 60 * 60 * 1000,  amount: 1000 },
  monthly:     { label: 'Monthly',  durationMs: 30 * 24 * 60 * 60 * 1000, amount: 3000 },
};

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Normalise any Kenyan phone number to E.164 (254XXXXXXXXX).
 * Returns null for unrecognised inputs.
 * @param {string|null|undefined} raw
 * @returns {string|null}
 */
function normalizePhone(raw) {
  if (!raw) return null;
  const digits = String(raw).replace(/\D/g, '');
  if (digits.startsWith('0')   && digits.length === 10) return '254' + digits.slice(1);
  if (digits.startsWith('254') && digits.length === 12) return digits;
  if ((digits.startsWith('7') || digits.startsWith('1')) && digits.length === 9) return '254' + digits;
  return null;
}

/**
 * True when the subscription is active (with grace period support).
 * @param {FirebaseFirestore.DocumentData} sub
 * @returns {boolean}
 */
function isActiveSub(sub) {
  if (!sub || sub.status !== 'active') return false;
  if (sub.paid_until) {
    const paidUntilMs = sub.paid_until.toMillis
      ? sub.paid_until.toMillis()
      : Number(sub.paid_until);
    // Allow a 2-hour grace period so mid-campaign expiry doesn't block sends
    if (paidUntilMs + GRACE_PERIOD_MS < Date.now()) return false;
  }
  return true;
}

/**
 * Resolve a plan code from an explicit field or infer it from payment amount.
 * @param {string|null} rawPlan
 * @param {number} amount  KES
 * @returns {string}
 */
function resolvePlan(rawPlan, amount) {
  if (rawPlan) {
    const key = String(rawPlan).toLowerCase().trim();
    if (PLANS[key]) return key;
  }
  // Infer from amount (≥ threshold wins the larger plan)
  if (amount >= 3000) return 'monthly';
  if (amount >= 1000) return 'weekly';
  if (amount >= 200)  return 'daily';
  if (amount >= 100)  return 'twelve_hour';
  if (amount >= 60)   return 'six_hour';
  return 'daily'; // safe fallback
}

// ── 1. checkSubscription ─────────────────────────────────────────────────────
exports.checkSubscription = functions.https.onCall(async (data) => {
  const phone = normalizePhone(data.phone);
  if (!phone) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'A valid Kenyan phone number is required.'
    );
  }

  const snapshot = await db
    .collection('subscriptions')
    .where('phone', '==', phone)
    .orderBy('paid_until', 'desc')
    .limit(1)
    .get();

  if (snapshot.empty) {
    return { premium: false, plan: null, paid_until: null };
  }

  const sub    = snapshot.docs[0].data();
  const active = isActiveSub(sub);

  const paidUntilMs = sub.paid_until
    ? (sub.paid_until.toMillis ? sub.paid_until.toMillis() : Number(sub.paid_until))
    : null;

  functions.logger.info('checkSubscription', { phone, active, plan: sub.plan });

  return {
    premium:    active,
    plan:       sub.plan  || null,
    paid_until: paidUntilMs,
    device_id:  sub.device_id || null,
  };
});

// ── 2. cleanExpiredSubs ──────────────────────────────────────────────────────
exports.cleanExpiredSubs = functions.pubsub
  .schedule('0 2 * * *')
  .timeZone('Africa/Nairobi')
  .onRun(async () => {
    // Expire subs whose paid_until has passed (grace period already elapsed)
    const cutoff = admin.firestore.Timestamp.fromMillis(Date.now() - GRACE_PERIOD_MS);

    const expiredSnap = await db
      .collection('subscriptions')
      .where('status', '==', 'active')
      .where('paid_until', '<', cutoff)
      .get();

    if (expiredSnap.empty) {
      functions.logger.info('cleanExpiredSubs: no expired subscriptions.');
      return null;
    }

    const now   = admin.firestore.Timestamp.now();
    const batch = db.batch();
    expiredSnap.docs.forEach(doc =>
      batch.update(doc.ref, { status: 'expired', updated_at: now })
    );
    await batch.commit();

    functions.logger.info(`cleanExpiredSubs: expired ${expiredSnap.size} subscription(s).`);
    return null;
  });

// ── 3. onPaymentWebhook ──────────────────────────────────────────────────────
exports.onPaymentWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== 'POST') return res.status(405).send('Method Not Allowed');

  // Optional webhook-signature verification
  const webhookSecret = functions.config().webhook?.secret;
  if (webhookSecret) {
    const received = req.headers['x-intasend-signature']
                  || req.headers['x-webhook-signature']
                  || '';
    const expected = crypto
      .createHmac('sha256', webhookSecret)
      .update(JSON.stringify(req.body))
      .digest('hex');
    if (!received || received !== expected) {
      functions.logger.warn('onPaymentWebhook: invalid signature');
      return res.status(401).send('Unauthorized');
    }
  }

  const body    = req.body  || {};
  const payload = body.data || body.payload || body;
  const reference = payload.reference || payload.id || null;

  const phone = normalizePhone(
    payload.phone || payload.customer_phone ||
    payload.customer?.phone || payload.msisdn || null
  );
  if (!phone) {
    functions.logger.warn('onPaymentWebhook: missing phone', { payload });
    return res.status(400).json({ error: 'phone_missing' });
  }

  // Duplicate-detection: reject if this reference was already processed
  if (reference) {
    const dupSnap = await db
      .collection('subscriptions').doc(phone)
      .collection('payment_history')
      .where('reference', '==', reference)
      .limit(1)
      .get();
    if (!dupSnap.empty) {
      functions.logger.info('onPaymentWebhook: duplicate reference, skipping', { reference });
      return res.status(200).json({ success: true, duplicate: true });
    }
  }

  const amount = Number(payload.amount) || 0;
  const plan   = resolvePlan(payload.plan || payload.subscription_plan, amount);
  const planDef = PLANS[plan];

  const now      = new Date();
  const paidUntil = new Date(now.getTime() + planDef.durationMs);

  const subData = {
    phone,
    plan,
    status:     'active',
    paid_until: admin.firestore.Timestamp.fromDate(paidUntil),
    amount,
    updated_at: admin.firestore.FieldValue.serverTimestamp(),
    raw_ref:    reference,
  };

  const subRef = db.collection('subscriptions').doc(phone);
  await subRef.set(subData, { merge: true });

  // Append to payment history
  await subRef.collection('payment_history').add({
    reference,
    plan,
    amount,
    paid_until: admin.firestore.Timestamp.fromDate(paidUntil),
    processed_at: admin.firestore.FieldValue.serverTimestamp(),
  });

  functions.logger.info('onPaymentWebhook: activated', {
    phone, plan, paid_until: paidUntil.toISOString()
  });
  return res.status(200).json({
    success:    true,
    phone,
    plan,
    paid_until: paidUntil.toISOString(),
  });
});

// ── 4. getSubscriptionPlans ──────────────────────────────────────────────────
exports.getSubscriptionPlans = functions.https.onCall(async () => {
  // Return the plan catalogue so the app never hard-codes prices
  return { plans: PLANS };
});

// ── 5. revokeSubscription (admin only) ──────────────────────────────────────
exports.revokeSubscription = functions.https.onCall(async (data, context) => {
  const adminUid = functions.config().admin?.uid;
  if (!adminUid || !context.auth || context.auth.uid !== adminUid) {
    throw new functions.https.HttpsError('permission-denied', 'Admin access required.');
  }

  const phone = normalizePhone(data.phone);
  if (!phone) {
    throw new functions.https.HttpsError('invalid-argument', 'Valid phone required.');
  }

  const now = admin.firestore.Timestamp.now();
  await db.collection('subscriptions').doc(phone).set({
    status:     'revoked',
    paid_until: now,
    updated_at: now,
  }, { merge: true });

  functions.logger.info('revokeSubscription', { phone, by: adminUid });
  return { success: true, phone };
});
