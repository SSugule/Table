const assert = require('assert');
const { calculateNextVersion } = require('./bump-version');

console.log('Testing calculateNextVersion...');

assert.strictEqual(calculateNextVersion('0.0.1'), '0.0.2');
assert.strictEqual(calculateNextVersion('0.0.9'), '0.1.0');
assert.strictEqual(calculateNextVersion('0.1.9'), '0.2.0');
assert.strictEqual(calculateNextVersion('0.9.9'), '1.0.0');
assert.strictEqual(calculateNextVersion('1.9.9'), '2.0.0');
assert.strictEqual(calculateNextVersion('9.9.9'), '9.9.9a');
assert.strictEqual(calculateNextVersion('9.9.9a'), '9.9.9b');
assert.strictEqual(calculateNextVersion('9.9.9y'), '9.9.9z');
assert.strictEqual(calculateNextVersion('9.9.9z'), '9.9.9z');
assert.strictEqual(calculateNextVersion('v0.1.1'), '0.1.2');

console.log('All versioning tests passed successfully!');
