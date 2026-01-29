// Test setup for Firebase functions
const admin = require('firebase-admin');
const test = require('firebase-functions-test');

// Initialize test environment
const testEnv = test();

// Mock Firebase admin initialization before requiring any modules
const mockApp = {
  firestore: () => ({
    collection: () => ({
      doc: () => ({
        get: () => Promise.resolve({}),
        set: () => Promise.resolve(),
        update: () => Promise.resolve(),
        delete: () => Promise.resolve()
      }),
      add: () => Promise.resolve({ id: 'test-id' }),
      where: () => ({
        get: () => Promise.resolve({ docs: [] })
      })
    })
  }),
  messaging: () => ({
    send: () => Promise.resolve('test-message-id')
  })
};

// Mock initializeApp to return our mock app
admin.initializeApp = () => mockApp;
admin.firestore = () => mockApp.firestore();
admin.messaging = () => mockApp.messaging();

module.exports = { admin, testEnv };
