import { db } from "../../shared/firebase.js";
import { resolveRole, waitForUser } from "../../shared/roles.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
const $ = id => document.getElementById(id);
let retailerId;
let currentSubscription = null;
const paymentLabel = method => method === "maya_sandbox" ? "Maya Sandbox" : "Previous pilot payment";

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
    ? `${planName} — Active`
    : d?.status === "demo"
      ? "Plan selected. Checkout is incomplete."
      : "No active subscription.";

  // Kiosk entitlement is separate from actual device activation.
  $("kiosk-status").textContent =
    isActive && isBundle
      ? "Kiosk access is included in your plan. Device activation is not yet available."
      : "No active kiosk entitlement.";

  // Display latest simulated transaction.
  $("request-history").textContent = isActive
    ? `Latest checkout: ₱${amount}.00 · ${paymentLabel(d.paymentMethod)}`
    : "No completed payments yet.";

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
    $("activePaymentMethod").textContent = paymentLabel(d.paymentMethod);
  }

  show(
    isActive
      ? "Your subscription is active."
      : "Choose the plan that fits your shop."
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

  $("mayaCheckoutError").classList.add("d-none");


  paymentModal.show();
}

async function mayaRequest(action, payload) {
  const user = await waitForUser();
  if (!user) throw new Error("Sign in to use Maya test checkout.");
  let response;
  try {
    response = await fetch(`http://localhost:3000/subscriptions/maya-sandbox/${action}`, {
      method: "POST", headers: { Authorization: `Bearer ${await user.getIdToken()}`, "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  } catch {
    throw new Error("FaceFit’s local payment server is unavailable.");
  }
  const result = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(result.error || "Maya test checkout is unavailable.");
  return result;
}

$("startMayaCheckout").addEventListener("click", async () => {
  const button = $("startMayaCheckout");
  const feedback = $("mayaCheckoutError");
  button.disabled = true;
  feedback.classList.add("d-none");
  try {
    const result = await mayaRequest("start", { planType: selectedPlan });
    location.assign(result.url);
  } catch (error) {
    feedback.textContent = error.message;
    feedback.classList.remove("d-none");
  } finally {
    button.disabled = false;
  }
});

async function checkMayaPayment() {
  const page = new URL(location.href);
  const reference = page.searchParams.get("mayaCheckout");
  if (!reference) return;
  const button = $("checkMayaPayment");
  button.classList.remove("d-none");
  button.disabled = true;
  show("Checking your Maya test payment…");
  try {
    const result = await mayaRequest("confirm", { reference });
    if (result.status === "pending") {
      show("Maya hasn’t confirmed the test payment yet. Check again in a moment.");
      return;
    }
    if (result.status === "confirmed") await refresh();
    show(result.status === "confirmed"
      ? "Checkout verified by Maya Sandbox. Your plan is active."
      : "The Maya test checkout ended without payment. Your plan is unchanged.");
    page.searchParams.delete("mayaCheckout");
    history.replaceState(null, "", page);
    button.classList.add("d-none");
  } catch (error) {
    show(error.message, true);
  } finally {
    button.disabled = false;
  }
}

$("checkMayaPayment").addEventListener("click", checkMayaPayment);

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

  changePlanModal.hide();

  choosePlan(newPlan);

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
    await mayaRequest("cancel-plan", {});

    cancelModal.hide();

    await refresh();

    show(
      "Your subscription has been cancelled. You can subscribe again anytime."
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
    retailerId = role.id;
    await refresh();
    await checkMayaPayment();
    $("request-retailer").onclick = () => choosePlan("retailer");
    $("request-bundle").onclick = () => choosePlan("bundle");
  }
} catch (e) { show(`Could not verify retailer account: ${e.message}`, true); }
