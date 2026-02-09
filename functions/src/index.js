// const functions = require('firebase-functions'); // intentionally unused (billing removed)
const admin = require('firebase-admin');
admin.initializeApp();

// Billing & subscription functionality removed intentionally.
// Scheduled jobs and Firestore triggers related to billing were removed.
// This file intentionally exports no billing functions to keep deployments stable.

module.exports = {}; 