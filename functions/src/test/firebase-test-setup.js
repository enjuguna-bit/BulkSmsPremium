// Test setup for Firebase functions
const admin = require('firebase-admin');
const test = require('firebase-functions-test');

// Initialize test environment with offline mode
const testEnv = test({
  databaseURL: 'https://test.firebaseio.com',
  projectId: 'test-project'
});

// Initialize Firebase Admin SDK with test credentials
try {
  admin.initializeApp({
    projectId: 'test-project',
    databaseURL: 'https://test.firebaseio.com'
  });
} catch {
  // App may already be initialized in tests
}

// Export for use in tests
module.exports = { admin, testEnv };
