const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");
const vm = require("node:vm");

function fixture() {
  const queued = new Map();
  const context = { exports: {}, require: name => ({
    "firebase-functions/v2/https": { onCall: handler => handler },
    "firebase-functions/v2/firestore": { onDocumentUpdated: (_options, handler) => handler },
    "firebase-admin/app": { initializeApp() {} },
    "firebase-admin/auth": { getAuth: () => ({ getUser: async uid => {
      assert.equal(uid, "test-retailer");
      return { email: "test@example.com" };
    } }) },
    "firebase-admin/firestore": { getFirestore: () => ({ collection: name => {
      assert.equal(name, "mail");
      return { doc: id => ({ create: async data => {
        if (queued.has(id)) throw Object.assign(new Error("Duplicate event"), { code: 6 });
        queued.set(id, data);
      } }) };
    } }) },
  })[name] };
  vm.runInNewContext(readFileSync(path.join(__dirname, "index.js"), "utf8"), context);
  const run = (before, after, owner = "test-retailer") => context.exports.queueRetailerDecisionEmail({
    id: "test-event", params: { retailerId: "test-retailer" }, data: {
      before: { data: () => ({ status: before, userId: "test-retailer" }) },
      after: { data: () => ({ status: after, userId: owner, emailAddress: "ignore@example.com" }) },
    },
  });
  return { run, queued };
}

test("approval queues one email to the Auth address even when the event retries", async () => {
  const { run, queued } = fixture();
  await run("pending", "approved");
  await run("pending", "approved");
  assert.equal(queued.size, 1);
  const email = queued.get("test-event");
  assert.equal(email.to.join(), "test@example.com");
  assert.match(email.message.subject, /approved/);
});

test("rejection queues the rejection message", async () => {
  const { run, queued } = fixture();
  await run("pending", "rejected");
  assert.match(queued.get("test-event").message.text, /not approved/);
});

test("profile edits and mismatched retailer identities do not queue email", async () => {
  const { run, queued } = fixture();
  await run("approved", "approved");
  await run("pending", "pending");
  await run("pending", "approved", "different-retailer");
  assert.equal(queued.size, 0);
});
