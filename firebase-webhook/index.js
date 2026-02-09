// Firebase Cloud Function for Intasend Webhooks
// Deploy this to get your webhook URL

const functions = require('firebase-functions');
const admin = require('firebase-admin');
const crypto = require('crypto');

admin.initializeApp();
const db = admin.firestore();

// Your webhook secret from Intasend
const WEBHOOK_SECRET = '6f14f10a47e82ca405fd9f4ea86b4b203edbbf0d70c2e7086a15c3dd1a671cba';

/**
 * Verify Intasend webhook signature
 */
function verifySignature(payload, signature) {
    const hash = crypto
        .createHmac('sha256', WEBHOOK_SECRET)
        .update(payload)
        .digest('hex');
    
    return hash === signature;
}

/**
 * Main webhook endpoint
 * URL will be: https://YOUR_PROJECT.cloudfunctions.net/intasendWebhook
 */
exports.intasendWebhook = functions.https.onRequest(async (req, res) => {
    // Only accept POST requests
    if (req.method !== 'POST') {
        return res.status(405).send({ error: 'Method not allowed' });
    }

    try {
        // Get signature from headers
        const signature = req.headers['x-intasend-signature'];
        const payload = JSON.stringify(req.body);
        
        // Verify signature for security
        if (!verifySignature(payload, signature)) {
            console.error('Invalid webhook signature');
            return res.status(401).send({ error: 'Invalid signature' });
        }

        const event = req.body;
        console.log('Received webhook event:', event.event);

        // Handle different event types
        switch (event.event) {
            case 'payment.success':
                await handlePaymentSuccess(event.data);
                break;
                
            case 'payment.failed':
                await handlePaymentFailed(event.data);
                break;
                
            case 'subscription.created':
                await handleSubscriptionCreated(event.data);
                break;
                
            case 'subscription.charge.success':
                await handleSubscriptionCharge(event.data);
                break;
                
            case 'subscription.charge.failed':
                await handleSubscriptionChargeFailed(event.data);
                break;
                
            case 'subscription.cancelled':
                await handleSubscriptionCancelled(event.data);
                break;
                
            default:
                console.log('Unhandled event type:', event.event);
        }

        // Always return 200 to acknowledge receipt
        res.status(200).send({ received: true });
        
    } catch (error) {
        console.error('Webhook error:', error);
        res.status(500).send({ error: error.message });
    }
});

/**
 * Handle successful payment
 */
async function handlePaymentSuccess(data) {
    console.log('Payment successful:', data.invoice_id);
    
    try {
        // Update transaction in Firestore
        await db.collection('transactions').doc(data.invoice_id).set({
            invoiceId: data.invoice_id,
            phoneNumber: data.account,
            amount: parseFloat(data.value),
            currency: data.currency,
            status: 'success',
            mpesaReference: data.mpesa_reference,
            apiRef: data.api_ref,
            charges: parseFloat(data.charges),
            netAmount: parseFloat(data.net_amount),
            provider: data.provider,
            completedAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
        
        // Update user subscription status if needed
        if (data.api_ref && data.api_ref.includes('BULKSMS')) {
            const phoneNumber = data.account;
            await db.collection('subscriptions').doc(phoneNumber).set({
                phoneNumber: phoneNumber,
                isActive: true,
                lastPaymentDate: admin.firestore.FieldValue.serverTimestamp(),
                lastPaymentAmount: parseFloat(data.value),
                totalPayments: admin.firestore.FieldValue.increment(1),
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            }, { merge: true });
        }
        
        console.log('Payment recorded successfully');
    } catch (error) {
        console.error('Error handling payment success:', error);
        throw error;
    }
}

/**
 * Handle failed payment
 */
async function handlePaymentFailed(data) {
    console.log('Payment failed:', data.invoice_id);
    
    try {
        await db.collection('transactions').doc(data.invoice_id).set({
            invoiceId: data.invoice_id,
            phoneNumber: data.account,
            amount: parseFloat(data.value),
            status: 'failed',
            failedReason: data.failed_reason,
            failedCode: data.failed_code,
            apiRef: data.api_ref,
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
        
        // Update subscription failed payment count
        if (data.api_ref && data.api_ref.includes('BULKSMS')) {
            const phoneNumber = data.account;
            await db.collection('subscriptions').doc(phoneNumber).update({
                failedPayments: admin.firestore.FieldValue.increment(1),
                lastFailureReason: data.failed_reason,
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        }
        
    } catch (error) {
        console.error('Error handling payment failure:', error);
    }
}

/**
 * Handle subscription created
 */
async function handleSubscriptionCreated(data) {
    console.log('Subscription created:', data.id);
    
    try {
        await db.collection('subscriptions').doc(data.phone_number).set({
            subscriptionId: data.id,
            phoneNumber: data.phone_number,
            amount: parseFloat(data.amount),
            interval: data.interval,
            status: data.status,
            apiRef: data.api_ref,
            nextChargeDate: new Date(data.next_charge_date),
            isActive: true,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
        
    } catch (error) {
        console.error('Error handling subscription creation:', error);
    }
}

/**
 * Handle successful subscription charge
 */
async function handleSubscriptionCharge(data) {
    console.log('Subscription charge successful:', data.subscription_id);
    
    await handlePaymentSuccess(data.payment);
    
    // Update subscription last charge date
    try {
        const subsDoc = await db.collection('subscriptions')
            .where('subscriptionId', '==', data.subscription_id)
            .limit(1)
            .get();
            
        if (!subsDoc.empty) {
            const docId = subsDoc.docs[0].id;
            await db.collection('subscriptions').doc(docId).update({
                lastChargeDate: admin.firestore.FieldValue.serverTimestamp(),
                nextChargeDate: new Date(data.next_charge_date),
                totalPayments: admin.firestore.FieldValue.increment(1),
                failedPayments: 0 // Reset failed count on success
            });
        }
    } catch (error) {
        console.error('Error updating subscription:', error);
    }
}

/**
 * Handle failed subscription charge
 */
async function handleSubscriptionChargeFailed(data) {
    console.log('Subscription charge failed:', data.subscription_id);
    
    await handlePaymentFailed(data.payment);
}

/**
 * Handle subscription cancellation
 */
async function handleSubscriptionCancelled(data) {
    console.log('Subscription cancelled:', data.id);
    
    try {
        const subsDoc = await db.collection('subscriptions')
            .where('subscriptionId', '==', data.id)
            .limit(1)
            .get();
            
        if (!subsDoc.empty) {
            const docId = subsDoc.docs[0].id;
            await db.collection('subscriptions').doc(docId).update({
                status: 'cancelled',
                isActive: false,
                cancelledAt: admin.firestore.FieldValue.serverTimestamp(),
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        }
    } catch (error) {
        console.error('Error handling cancellation:', error);
    }
}
