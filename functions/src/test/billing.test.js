const chai = require('chai');
const expect = chai.expect;
const sinon = require('sinon');
const { admin, testEnv } = require('./firebase-test-setup');

// Wrap the billing module with test environment
const billing = testEnv.wrap(require('../billing'));

describe('Billing Functions', () => {
  let firestoreStub;
  let messagingStub;
  
  beforeEach(() => {
    firestoreStub = sinon.stub(admin.firestore());
    messagingStub = sinon.stub(admin.messaging());
  });
  
  afterEach(() => {
    sinon.restore();
  });
  
  describe('sendSubscriptionReminders', () => {
    it('should send reminders for expiring subscriptions', async () => {
      // Mock data
      const mockQuery = {
        where: sinon.stub().returnsThis(),
        get: sinon.stub().resolves({
          docs: [{
            data: () => ({
              plan_id: 'basic',
              end_date: Date.now() + (24 * 60 * 60 * 1000), // Tomorrow
              auto_renew: false
            }),
            ref: { parent: { parent: { id: 'user123' } } }
          }]
        })
      };
      
      firestoreStub.collectionGroup.returns(mockQuery);
      
      const result = await billing.sendSubscriptionReminders();
      
      expect(result.success).to.be.true;
      expect(result.sent).to.equal(1);
    });
  });
  
  describe('checkExpiredSubscriptions', () => {
    it('should expire old subscriptions', async () => {
      const mockDoc = {
        ref: {
          update: sinon.stub().resolves(),
          parent: { parent: { id: 'user123' } }
        }
      };
      
      const mockQuery = {
        where: sinon.stub().returnsThis(),
        get: sinon.stub().resolves({ docs: [mockDoc] })
      };
      
      firestoreStub.collectionGroup.returns(mockQuery);
      firestoreStub.collection().doc().update.resolves();
      
      const result = await billing.checkExpiredSubscriptions();
      
      expect(result.success).to.be.true;
      expect(result.expired).to.equal(1);
    });
  });
});