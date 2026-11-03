import { db } from "../../shared/firebase.js";
import { resolveRole, waitForUser } from "../../shared/roles.js";
import { doc, getDoc, setDoc, collection, writeBatch, serverTimestamp } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
const $ = id => document.getElementById(id);
let retailerId, uid;
let currentSubscription = null;

const subscriptionPlans = {
  retailer: {
    name: "Retailer Plan",
    price: 499
  },
  bundle: {
    name: "Retailer + Kiosk",
    price: 798
  }
};
let checkoutMode = "subscribe";
function show(message, error=false) { $("feedback").className = `alert alert-${error ? "danger" : "info"}`; $("feedback").textContent = message; }

async function refresh() {
  const snap = await getDoc(doc(db, "subscriptions", retailerId));
  const d = snap.exists() ? snap.data() : null;
  currentSubscription = d;

  const isActive =
    d?.status === "demo_active" &&
    d?.paymentMode === "simulation";

  const isBundle = d?.planType === "bundle";
  const planName = isBundle ? "Retailer + Kiosk" : "Retailer Plan";
  const amount = isBundle ? 798 : 499;

  // Show or hide the active subscription management section.
  $("activeSubscriptionSection").classList.toggle("d-none", !isActive);
  $("changeSubscriptionPlan").disabled = !isActive;
  $("cancelSubscription").disabled = !isActive;

  // Display current subscription.
  $("current-status").textContent = isActive
    ? `${planName} — Active (Simulated)`
    : d?.status === "demo"
      ? "Demo plan selected — payment not completed."
      : "No active subscription.";

  // Kiosk entitlement is separate from actual device activation.
  $("kiosk-status").textContent =
    isActive && isBundle
      ? "Kiosk included in demo plan. Device activation is not yet implemented."
      : "No active kiosk entitlement.";

  // Display latest simulated transaction.
  $("request-history").textContent = isActive
    ? `Latest simulated payment: ₱${amount}.00 via ${
        d.paymentMethod === "gcash"
          ? "GCash Demo"
          : "Credit/Debit Demo"
      }. No real charge was made.`
    : "No completed simulated payments yet.";

  // Update plan selection buttons.
  const retailerButton = $("request-retailer");
  const bundleButton = $("request-bundle");

  retailerButton.disabled = false;
  bundleButton.disabled = false;

  retailerButton.textContent = "Select Retailer Plan";
  bundleButton.textContent = "Select Retailer + Kiosk";

  if (isActive) {
    const currentButton = isBundle ? bundleButton : retailerButton;
    const otherButton = isBundle ? retailerButton : bundleButton;

    currentButton.textContent = "Current Plan";
    currentButton.disabled = true;

    // Switching plans will be handled by the Change Plan flow.
    otherButton.textContent = "Available Plan";
    otherButton.disabled = true;

    $("activePlanName").textContent = planName;
    $("activePlanPrice").textContent = `₱${amount}.00 / month`;
    $("activePaymentMethod").textContent =
      d.paymentMethod === "gcash"
        ? "GCash Demo"
        : "Credit/Debit Demo";
  }

  show(
    isActive
      ? "Your simulated subscription is active. No real payment was collected."
      : "Choose a demo subscription plan. No real payment will be charged."
  );
}



let selectedPlan = null;

const paymentModal = new bootstrap.Modal(
  document.getElementById("paymentModal")
);

function choosePlan(planType) {
  selectedPlan = planType;

  $("checkoutPlan").textContent =
    planType === "bundle"
      ? "Retailer + Kiosk"
      : "Retailer Plan";

  $("checkoutAmount").textContent =
    planType === "bundle"
      ? "₱798.00"
      : "₱499.00";

  $("checkoutFormStep").classList.remove("d-none");
  $("checkoutReviewStep").classList.add("d-none");
  $("checkoutSuccessStep").classList.add("d-none");
  $("checkoutError").classList.add("d-none");


  paymentModal.show();
}

document.querySelectorAll(
  'input[name="paymentMethod"]'
).forEach((radio) => {
  radio.addEventListener("change", () => {
    const method = document.querySelector(
      'input[name="paymentMethod"]:checked'
    ).value;

    $("gcashFields").classList.toggle(
      "d-none", method !== "gcash"
    );

    $("cardFields").classList.toggle(
      "d-none", method !== "card"
    );
  });
});

function getPaymentMethod() {
  return document.querySelector(
    'input[name="paymentMethod"]:checked'
  ).value;
}

function showCheckoutError(message) {
  const error = $("checkoutError");
  error.textContent = message;
  error.classList.remove("d-none");
}

$("reviewDemoPayment").addEventListener("click", () => {
  $("checkoutError").classList.add("d-none");

  const method = getPaymentMethod();

  
if (method === "gcash") {
  const phone = $("demoPhone").value.replace(/[\s-]/g, "");

  if (!/^09\d{9}$/.test(phone)) {
    showCheckoutError(
      "Please enter a valid 11-digit demo mobile number starting with 09."
    );
    return;
  }

  // No real GCash MPIN is required or verified.
  // The payment is simulated for capstone testing.
} else {
  const cardNumber = $("demoCardNumber").value.replace(/[\s-]/g, "");
  const expiry = $("demoExpiry").value.trim();

  // Only accept clearly designated fictional test card numbers.
  const testCards = ["4242424242424242", "5555555555554444"];

  if (!testCards.includes(cardNumber)) {
    showCheckoutError(
      "Use a supported demo card: 4242 4242 4242 4242 or 5555 5555 5555 4444."
    );
    return;
  }

  if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(expiry)) {
    showCheckoutError("Enter a valid demo expiration date in MM/YY format.");
    return;
  }

  const billingFields = [
    "billingFirstName",
    "billingLastName",
    "billingAddress",
    "billingCity",
    "billingPostal",
    "billingPhone"
  ];

  if (billingFields.some(id => !$(id).value.trim())) {
    showCheckoutError("Please complete all billing information.");
    return;
  }
}

  const planName = selectedPlan === "bundle"
    ? "Retailer + Kiosk"
    : "Retailer Plan";

  const amount = selectedPlan === "bundle" ? "₱798.00" : "₱499.00";

  $("checkoutReviewSummary").textContent =
    `${planName} — ${amount}/month. Payment method: ${
      method === "gcash" ? "GCash Demo" : "Credit/Debit Demo"
    }.`;

  $("checkoutFormStep").classList.add("d-none");
  $("checkoutReviewStep").classList.remove("d-none");
});

$("backToCheckout").addEventListener("click", () => {
  $("checkoutReviewStep").classList.add("d-none");
  $("checkoutFormStep").classList.remove("d-none");
});

$("confirmDemoPayment").addEventListener("click", async () => {
  if (!selectedPlan || !retailerId || !uid) {
    alert("Your retailer account is not ready. Please refresh the page.");
    return;
  }

  const button = $("confirmDemoPayment");
  const method = getPaymentMethod();
  const amount = selectedPlan === "bundle" ? 798 : 499;

  button.disabled = true;
  button.innerHTML =
    '<span class="spinner-border spinner-border-sm me-2"></span>Processing...';

  try {
    if (checkoutMode === "change") {
  const currentSnap = await getDoc(
    doc(db, "subscriptions", retailerId)
  );

  if (!currentSnap.exists()) {
    throw new Error("No existing subscription found.");
  }

  const current = currentSnap.data();

  if (
    current.userId !== uid ||
    current.status !== "demo_active" ||
    current.paymentMode !== "simulation"
  ) {
    throw new Error("An active demo subscription is required to change plans.");
  }

  if (current.planType === selectedPlan) {
    throw new Error("You are already subscribed to this plan.");
  }
}

    const batch = writeBatch(db);
    batch.set(doc(db, "subscriptions", retailerId), {
      retailerId: retailerId,
      userId: uid,
      planType: selectedPlan,
      status: "demo_active",
      paymentMode: "simulation",
      paymentMethod: method,
      amount: amount,
      currency: "PHP",
      updatedAt: serverTimestamp()
    });
    const notificationRef = doc(collection(db, "notifications"));
    const change = checkoutMode === "change";
    batch.set(notificationRef, {
      userId: uid,
      retailerId,
      type: change ? "subscription_changed" : "subscription_activated",
      title: change ? "Subscription plan changed" : "Demo subscription activated",
      message: `${change ? "Your plan has changed to" : "Your demo subscription is now active on"} ${selectedPlan === "bundle" ? "Retailer + Kiosk" : "Retailer Plan"} (₱${amount}/month). No real payment was charged.`,
      createdAt: serverTimestamp(),
      readAt: null
    });
    await batch.commit();

    // Display successful demo payment.
    $("demoReceiptDetails").textContent =
      `Your ${selectedPlan === "bundle" ? "Retailer + Kiosk" : "Retailer"} ` +
      `subscription is now active (demo). ₱${amount}.00 via ` +
      `${method === "gcash" ? "GCash Demo" : "Credit/Debit Demo"}. ` +
      `No real payment was processed.`;

    $("checkoutReviewStep").classList.add("d-none");
    $("checkoutSuccessStep").classList.remove("d-none");

    await refresh();

  } catch (error) {
    console.error("Demo subscription error:", error);
    alert("Unable to activate demo subscription: " + error.message);
  } finally {
    button.disabled = false;
    button.textContent = "Confirm Simulated Payment";
  }
});

$("finishDemoPayment").addEventListener("click", () => {
  paymentModal.hide();

  checkoutMode = "subscribe";
  selectedPlan = null;
});

const cancelModal = new bootstrap.Modal(
  document.getElementById("cancelSubscriptionModal")
);

const changePlanModal = new bootstrap.Modal(
  document.getElementById("changePlanModal")
);

$("changeSubscriptionPlan").addEventListener("click", () => {
  if (
    !currentSubscription ||
    currentSubscription.status !== "demo_active" ||
    currentSubscription.paymentMode !== "simulation"
  ) {
    return;
  }

  const currentPlan = currentSubscription.planType;

  const newPlan = currentPlan === "retailer"
    ? "bundle"
    : "retailer";

  const current = subscriptionPlans[currentPlan];
  const next = subscriptionPlans[newPlan];

  if (!current || !next) {
    console.error("Unknown subscription plan.");
    return;
  }

  $("changeCurrentPlan").textContent =
    `${current.name} — ₱${current.price}/month`;

  $("changeNewPlan").textContent = next.name;

  $("changeNewPrice").textContent =
    `₱${next.price}/month`;

  $("proceedChangePlan").dataset.plan = newPlan;

  changePlanModal.show();
});

$("proceedChangePlan").addEventListener("click", () => {
  const newPlan = $("proceedChangePlan").dataset.plan;

  if (!subscriptionPlans[newPlan]) {
    console.error("Invalid plan selected.");
    return;
  }

  checkoutMode = "change";

  changePlanModal.hide();

  choosePlan(newPlan);

  // Next: open the existing checkout with this plan.
});

// Open the confirmation window.
$("cancelSubscription").addEventListener("click", () => {
  $("cancelSubscriptionError").classList.add("d-none");
  cancelModal.show();
});

// Confirm cancellation and update Firebase.
$("confirmCancelSubscription").addEventListener("click", async () => {
  const button = $("confirmCancelSubscription");

  button.disabled = true;
  button.innerHTML =
    '<span class="spinner-border spinner-border-sm me-2"></span>Cancelling...';

  try {
    if (!retailerId || !uid) {
      throw new Error("Retailer account is not ready.");
    }

    const subscriptionRef = doc(db, "subscriptions", retailerId);
    const snap = await getDoc(subscriptionRef);

    if (!snap.exists()) {
      throw new Error("No subscription found.");
    }

    const subscription = snap.data();

    if (
      subscription.userId !== uid ||
      subscription.status !== "demo_active" ||
      subscription.paymentMode !== "simulation"
    ) {
      throw new Error("No active demo subscription to cancel.");
    }

    const batch = writeBatch(db);
    batch.set(subscriptionRef, {
      ...subscription,
      status: "demo_cancelled",
      updatedAt: serverTimestamp()
    });
    batch.set(doc(collection(db, "notifications")), {
      userId: uid,
      retailerId,
      type: "subscription_cancelled",
      title: "Demo subscription cancelled",
      message: "Your demo subscription has been cancelled. You can subscribe again anytime.",
      createdAt: serverTimestamp(),
      readAt: null
    });
    await batch.commit();

    cancelModal.hide();

    await refresh();

    show(
      "Your demo subscription has been cancelled. You can subscribe again anytime."
    );

  } catch (error) {
    console.error("Subscription cancellation failed:", error);

    $("cancelSubscriptionError").textContent =
      "Unable to cancel subscription: " + error.message;

    $("cancelSubscriptionError").classList.remove("d-none");

  } finally {
    button.disabled = false;
    button.textContent = "Yes, Cancel Subscription";
  }
});

try {
  const user = await waitForUser();
  const role = await resolveRole(user);
  if (role.role !== "retailer" || role.data.status !== "approved") {
    location.replace(role.role === "retailer" ? "pending.html" : "../admin/admin/login.html");
  } else {
    uid = user.uid; retailerId = role.id;
    await refresh();
    $("request-retailer").onclick = () => {
  checkoutMode = "subscribe";
  choosePlan("retailer");
};

$("request-bundle").onclick = () => {
  checkoutMode = "subscribe";
  choosePlan("bundle");
};
  }
} catch (e) { show(`Could not verify retailer account: ${e.message}`, true); }
