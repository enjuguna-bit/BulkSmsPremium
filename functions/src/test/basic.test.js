const chai = require('chai');
const expect = chai.expect;

describe('Basic Tests', () => {
  it('should pass a simple test', () => {
    expect(true).to.be.true;
  });
  
  it('should handle basic math', () => {
    expect(2 + 2).to.equal(4);
  });
});
