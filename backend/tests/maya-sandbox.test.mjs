import assert from "node:assert/strict";
import { test } from "node:test";
import { createMayaSandbox } from "../maya-sandbox.js";

const checkoutId = "6baa9386-56fb-4d52-afaf-b75782ec870e";
const origin = "http://127.0.0.1:5500";

function fixture() {
  const docs = new Map([["retailers/shop-a", { status: "approved" }], ["retailers/shop-b", { status: "approved" }]]);
  const requests = [];
  let notificationNumber = 0;
  let payment = { status: "AUTHENTICATING", isPaid: false };
  let subscriptionVersion;
  const snapshot = ref => ({ data: () => docs.get(ref.path), ref, updateTime: ref.path.startsWith("subscriptions/") ? subscriptionVersion : undefined });
  const db = {
    doc: path => ({ path, get: async () => snapshot({ path }), set: async data => docs.set(path, data) }),
    collection: name => ({ doc: () => ({ path: `${name}/${++notificationNumber}` }) }),
    runTransaction: async callback => callback({
      getAll: async (...refs) => refs.map(snapshot),
      set: (ref, data) => docs.set(ref.path, data),
      update: (ref, data) => docs.set(ref.path, { ...docs.get(ref.path), ...data }),
    }),
  };
  const checkout = createMayaSandbox({ db, publicKey: "test-public", secretKey: "test-secret", timestamp: () => "test-time",
    request: async (url, options) => {
      requests.push({ url, ...options });
      if (options.method === "POST") return Response.json({ checkoutId, redirectUrl: `https://payments-web-sandbox.maya.ph/v2/checkout?id=${checkoutId}` });
      const requestReferenceNumber = JSON.parse(requests[0].body).requestReferenceNumber;
      return Response.json({ id: checkoutId, requestReferenceNumber, amount: "798.00", currency: "PHP", ...payment });
    },
  });
  return { checkout, docs, requests, setPayment: value => { payment = value; }, changeSubscription: () => { subscriptionVersion = { toMillis: () => 1 }; } };
}

test("opens the hosted sandbox checkout with the backend plan price and trusted return URL", async () => {
  const { checkout, requests, docs } = fixture();
  const result = await checkout.start("shop-a", "bundle", origin);
  assert.match(result.url, /^https:\/\/payments-web-sandbox\.maya\.ph\//);
  assert.equal(requests[0].url, "https://pg-sandbox.paymaya.com/checkout/v1/checkouts");
  assert.equal(requests[0].headers.Authorization, `Basic ${Buffer.from("test-public:").toString("base64")}`);
  const requestBody = JSON.parse(requests[0].body);
  assert.deepEqual(requestBody.totalAmount, { value: 798, currency: "PHP" });
  assert.equal(requestBody.redirectUrl.success, `${origin}/retailer/subscription.html?mayaCheckout=${requestBody.requestReferenceNumber}`);
  assert.equal(docs.has("subscriptions/shop-a"), false);
  await assert.rejects(checkout.start("shop-a", "constructor", origin), { status: 400 });
  await assert.rejects(checkout.start("shop-a", "bundle", "https://example.com"), { status: 400 });
  await assert.rejects(checkout.start("shop-unknown", "bundle", origin), { status: 403 });
  assert.equal(requests.length, 1);
});

test("pending, cancelled and mismatched payments never activate a plan", async () => {
  const { checkout, requests, docs, setPayment } = fixture();
  await checkout.start("shop-a", "bundle", origin);
  const reference = docs.get("mayaSandboxCheckouts/shop-a").reference;
  await assert.rejects(checkout.confirm("shop-b", reference), { status: 404 });
  assert.deepEqual(await checkout.confirm("shop-a", reference), { status: "pending" });
  assert.equal(requests[1].headers.Authorization, `Basic ${Buffer.from("test-secret:").toString("base64")}`);
  setPayment({ status: "PAYMENT_CANCELLED", isPaid: false });
  assert.deepEqual(await checkout.confirm("shop-a", reference), { status: "closed" });
  setPayment({ status: "PAYMENT_SUCCESS", isPaid: true, amount: "499.00" });
  await assert.rejects(checkout.confirm("shop-a", reference), { status: 502 });
  assert.equal(docs.has("subscriptions/shop-a"), false);
  assert.equal(docs.has("subscriptions/shop-b"), false);
});

test("a successful matching Maya payment activates the demo once and never changes another retailer", async () => {
  const { checkout, docs, requests, setPayment } = fixture();
  await checkout.start("shop-a", "bundle", origin);
  const reference = docs.get("mayaSandboxCheckouts/shop-a").reference;
  setPayment({ status: "PAYMENT_SUCCESS", isPaid: true });
  assert.deepEqual(await checkout.confirm("shop-a", reference), { status: "confirmed", planType: "bundle" });
  const subscription = docs.get("subscriptions/shop-a");
  assert.equal(subscription.paymentMode, "simulation");
  assert.equal(subscription.paymentMethod, "maya_sandbox");
  assert.equal(subscription.status, "demo_active");
  assert.equal(docs.has("subscriptions/shop-b"), false);
  assert.equal((await checkout.confirm("shop-a", reference)).status, "confirmed");
  assert.equal(requests.length, 2);
  assert.equal([...docs.keys()].filter(path => path.startsWith("notifications/")).length, 1);
});

test("an older checkout cannot override a plan change made while paying", async () => {
  const { checkout, docs, setPayment, changeSubscription } = fixture();
  await checkout.start("shop-a", "bundle", origin);
  const reference = docs.get("mayaSandboxCheckouts/shop-a").reference;
  changeSubscription();
  setPayment({ status: "PAYMENT_SUCCESS", isPaid: true });
  await assert.rejects(checkout.confirm("shop-a", reference), { status: 409 });
  assert.equal(docs.has("subscriptions/shop-a"), false);
});

test("plan cancellation is scoped to the retailer and only creates one notification", async () => {
  const { checkout, docs } = fixture();
  docs.set("subscriptions/shop-a", { userId: "shop-a", status: "demo_active", paymentMode: "simulation", paymentMethod: "maya_sandbox" });
  await assert.rejects(checkout.cancelPlan("shop-b"), { status: 404 });
  assert.equal((await checkout.cancelPlan("shop-a")).status, "cancelled");
  assert.equal((await checkout.cancelPlan("shop-a")).status, "cancelled");
  assert.equal(docs.get("subscriptions/shop-a").status, "demo_cancelled");
  assert.equal(docs.get("subscriptions/shop-a").paymentMethod, "maya_sandbox");
  assert.equal([...docs.keys()].filter(path => path.startsWith("notifications/")).length, 1);
});
