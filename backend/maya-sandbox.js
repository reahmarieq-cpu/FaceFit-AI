import { randomUUID } from "node:crypto";

const sandboxApi = "https://pg-sandbox.paymaya.com";
const checkoutHosts = new Set(["payments-web-sandbox.maya.ph", "payments-web-sandbox.paymaya.com"]);
const plans = { retailer: { name: "Retailer Plan", amount: 499 }, bundle: { name: "Retailer + Kiosk", amount: 798 } };
const localOrigin = /^http:\/\/(?:localhost|127\.0\.0\.1):(?:5500|8000)$/;
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;

function reject(status, message) {
  throw Object.assign(new Error(message), { status });
}

export function createMayaSandbox({ db, publicKey, secretKey, timestamp, request = fetch }) {
  async function call(path, key, body) {
    if (!publicKey || !secretKey) reject(503, "Maya sandbox is not configured on this server.");
    const response = await request(`${sandboxApi}${path}`, {
      method: body ? "POST" : "GET",
      headers: { Authorization: `Basic ${Buffer.from(`${key}:`).toString("base64")}`, "Content-Type": "application/json", Accept: "application/json" },
      body: body ? JSON.stringify(body) : undefined,
      redirect: "error",
      signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) reject(502, "Maya sandbox could not process the request. Try again.");
    return response.json();
  }

  async function requireRetailer(uid) {
    const retailer = await db.doc(`retailers/${uid}`).get();
    if (retailer.data()?.status !== "approved") reject(403, "Approved retailer required.");
    return retailer.ref;
  }

  return {
    async start(uid, planType, origin) {
      if (!Object.hasOwn(plans, planType) || typeof planType !== "string") reject(400, "Choose a valid plan.");
      if (!localOrigin.test(origin || "")) reject(400, "Open FaceFit from its local web address to use Maya sandbox.");
      await requireRetailer(uid);
      const plan = plans[planType];
      const subscription = await db.doc(`subscriptions/${uid}`).get();
      const reference = randomUUID();
      const returnUrl = `${origin}/retailer/subscription.html?mayaCheckout=${reference}`;
      const checkout = await call("/checkout/v1/checkouts", publicKey, {
        totalAmount: { value: plan.amount, currency: "PHP" },
        items: [{ name: `${plan.name} (FaceFit test)`, quantity: 1, amount: { value: plan.amount }, totalAmount: { value: plan.amount } }],
        requestReferenceNumber: reference,
        redirectUrl: { success: returnUrl, failure: returnUrl, cancel: returnUrl },
      });
      const destination = new URL(checkout.redirectUrl);
      if (!uuid.test(checkout.checkoutId) || destination.protocol !== "https:" || destination.port || destination.username || destination.password || !checkoutHosts.has(destination.hostname)) {
        reject(502, "Maya returned an unexpected test checkout link.");
      }
      await db.doc(`mayaSandboxCheckouts/${uid}`).set({
        reference, checkoutId: checkout.checkoutId, planType, amount: plan.amount,
        subscriptionVersion: subscription.updateTime?.toMillis() ?? null,
        state: "pending", createdAt: timestamp(),
      });
      return { url: destination.href };
    },

    async confirm(uid, reference) {
      if (typeof reference !== "string" || !uuid.test(reference)) reject(400, "Invalid checkout reference.");
      const retailerRef = await requireRetailer(uid);
      const checkoutRef = db.doc(`mayaSandboxCheckouts/${uid}`);
      const subscriptionRef = db.doc(`subscriptions/${uid}`);
      const checkout = (await checkoutRef.get()).data();
      if (!checkout || checkout.reference !== reference) reject(404, "This checkout is no longer the latest one for your account.");
      if (checkout.state === "confirmed") return { status: "confirmed", planType: checkout.planType };
      const payment = await call(`/payments/v1/payments/${encodeURIComponent(checkout.checkoutId)}`, secretKey);
      if (payment.id !== checkout.checkoutId || payment.requestReferenceNumber !== reference || Number(payment.amount) !== checkout.amount || payment.currency !== "PHP") {
        reject(502, "The Maya payment details don’t match this checkout.");
      }
      if (payment.isPaid !== true || !["PAYMENT_SUCCESS", "CAPTURED"].includes(payment.status)) {
        const closed = ["AUTH_FAILED", "PAYMENT_FAILED", "PAYMENT_CANCELLED", "PAYMENT_EXPIRED", "VOIDED", "REFUNDED"].includes(payment.status);
        return { status: closed ? "closed" : "pending" };
      }
      return db.runTransaction(async transaction => {
        const [latest, retailer, subscription] = await transaction.getAll(checkoutRef, retailerRef, subscriptionRef);
        const current = latest.data();
        if (retailer.data()?.status !== "approved") reject(403, "Approved retailer required.");
        if (current?.reference !== reference) reject(409, "A newer test checkout was started.");
        if (current.state === "confirmed") return { status: "confirmed", planType: current.planType };
        if ((subscription.updateTime?.toMillis() ?? null) !== current.subscriptionVersion) reject(409, "Your plan changed during checkout. Start a new test checkout.");
        const subscriptionRecord = {
          retailerId: uid, userId: uid, planType: current.planType, status: "demo_active",
          paymentMode: "simulation", paymentMethod: "maya_sandbox", amount: current.amount, currency: "PHP", updatedAt: timestamp(),
        };
        transaction.set(subscriptionRef, subscriptionRecord);
        transaction.update(checkoutRef, { state: "confirmed", confirmedAt: timestamp() });
        transaction.set(db.collection("notifications").doc(), {
          userId: uid, retailerId: uid, type: "subscription_activated", title: "Maya test payment confirmed",
          message: `${plans[current.planType].name} is active. No real money was charged.`, createdAt: timestamp(), readAt: null,
        });
        return { status: "confirmed", planType: current.planType };
      });
    },

    async cancelPlan(uid) {
      const retailerRef = await requireRetailer(uid);
      const subscriptionRef = db.doc(`subscriptions/${uid}`);
      return db.runTransaction(async transaction => {
        const [retailer, snapshot] = await transaction.getAll(retailerRef, subscriptionRef);
        const subscription = snapshot.data();
        if (retailer.data()?.status !== "approved") reject(403, "Approved retailer required.");
        if (subscription?.userId !== uid || subscription.paymentMode !== "simulation") reject(404, "No test plan to cancel.");
        if (subscription.status === "demo_cancelled") return { status: "cancelled" };
        if (subscription.status !== "demo_active") reject(409, "No active test plan to cancel.");
        transaction.update(subscriptionRef, { status: "demo_cancelled", updatedAt: timestamp() });
        transaction.set(db.collection("notifications").doc(), {
          userId: uid, retailerId: uid, type: "subscription_cancelled", title: "Test plan cancelled",
          message: "Your test plan is no longer active. No real payment or refund was processed.", createdAt: timestamp(), readAt: null,
        });
        return { status: "cancelled" };
      });
    },
  };
}
