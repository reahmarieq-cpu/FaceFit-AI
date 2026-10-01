// ============================================================
// FaceFit AI — Retailer Application Detail / Review
// ------------------------------------------------------------
// Reads a single retailers/{id} document for display. Approval
// and rejection NEVER write to Firestore directly from here —
// both go through the approveRetailer / rejectRetailer Cloud
// Functions, which are the only trusted write path.
// ============================================================

import { guardAdminPage } from "./admin-guard.js";
import { logoutAdmin } from "./auth.js";
import { db, functions } from "./firebase-config.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
import { httpsCallable } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-functions.js";

const nameEl = document.getElementById("admin-name");
const emailEl = document.getElementById("admin-email");
const avatarEl = document.getElementById("admin-avatar");
const logoutBtn = document.getElementById("logout-btn");

const loadingState = document.getElementById("loading-state");
const errorState = document.getElementById("error-state");
const detailContent = document.getElementById("detail-content");
const statusBadge = document.getElementById("status-badge");
const reviewActions = document.getElementById("review-actions");
const approveBtn = document.getElementById("approve-btn");
const rejectBtn = document.getElementById("reject-btn");
const actionFeedback = document.getElementById("action-feedback");
const alreadyReviewedNote = document.getElementById("already-reviewed-note");

const approveRetailerFn = httpsCallable(functions, "approveRetailer");
const rejectRetailerFn = httpsCallable(functions, "rejectRetailer");

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

// ---------- URL param ----------

const retailerId = new URLSearchParams(window.location.search).get("id");

// ---------- Rendering helpers ----------

function statusBadgeClass(status) {
  if (status === "approved") return "badge-status badge-status-approved";
  if (status === "rejected") return "badge-status badge-status-rejected";
  return "badge-status badge-status-pending";
}

function statusLabel(status) {
  if (status === "approved") return "Approved";
  if (status === "rejected") return "Rejected";
  return "Pending";
}

function formatDate(timestamp) {
  if (!timestamp || typeof timestamp.toDate !== "function") return "—";
  return timestamp.toDate().toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit"
  });
}

function setText(id, value) {
  document.getElementById(id).textContent = value && value !== "" ? value : "—";
}

function renderRetailer(retailer) {
  setText("field-retailName", retailer.retailName);
  setText("field-contactPerson", retailer.contactPerson);
  setText("field-contactNumber", retailer.contactNumber);
  setText("field-emailAddress", retailer.emailAddress);
  setText("field-retailAddress", retailer.retailAddress);
  setText("field-permit", retailer.permit);
  setText("field-createdAt", formatDate(retailer.createdAt));
  setText("field-subscriptionPlan", retailer.subscriptionPlan);
  setText("field-subscriptionStatus", retailer.subscriptionStatus);

  statusBadge.className = statusBadgeClass(retailer.status);
  statusBadge.textContent = statusLabel(retailer.status);

  updateReviewControls(retailer.status);
}

/**
 * Shows the Approve/Reject buttons only while status is "pending".
 * Once reviewed, buttons are hidden and a note is shown instead —
 * this is the "prevent duplicate/repeated review actions" rule.
 */
function updateReviewControls(status) {
  const isPending = status === "pending";
  reviewActions.classList.toggle("d-none", !isPending);
  alreadyReviewedNote.classList.toggle("d-none", isPending);
}

function showActionFeedback(message, type) {
  actionFeedback.textContent = message;
  actionFeedback.className = `alert alert-${type}`;
  actionFeedback.classList.remove("d-none");
}

function setButtonsLoading(isLoading) {
  approveBtn.disabled = isLoading;
  rejectBtn.disabled = isLoading;
  approveBtn.textContent = isLoading && approveBtn.dataset.pending ? "Approving…" : "Approve";
  rejectBtn.textContent = isLoading && rejectBtn.dataset.pending ? "Rejecting…" : "Reject";
}

// ---------- Data loading ----------

async function loadRetailer() {
  if (!retailerId) {
    loadingState.classList.add("d-none");
    errorState.textContent = "No retailer ID was provided in the URL.";
    errorState.classList.remove("d-none");
    return;
  }

  try {
    const snap = await getDoc(doc(db, "retailers", retailerId));

    if (!snap.exists()) {
      loadingState.classList.add("d-none");
      errorState.textContent = `No retailer application found with ID "${retailerId}".`;
      errorState.classList.remove("d-none");
      return;
    }

    const retailer = snap.data();
    renderRetailer(retailer);

    loadingState.classList.add("d-none");
    detailContent.classList.remove("d-none");
  } catch (error) {
    console.error("Failed to load retailer application:", error);
    loadingState.classList.add("d-none");
    errorState.textContent =
      "Unable to load this retailer application. You may not have permission, or it may not exist.";
    errorState.classList.remove("d-none");
  }
}

// ---------- Approve / Reject ----------

async function handleDecision(decision) {
  const isApprove = decision === "approve";
  const fn = isApprove ? approveRetailerFn : rejectRetailerFn;
  const triggeringBtn = isApprove ? approveBtn : rejectBtn;

  triggeringBtn.dataset.pending = "true";
  setButtonsLoading(true);
  actionFeedback.classList.add("d-none");

  try {
    const result = await fn({ retailerId });
    const newStatus = result?.data?.status || (isApprove ? "approved" : "rejected");

    statusBadge.className = statusBadgeClass(newStatus);
    statusBadge.textContent = statusLabel(newStatus);
    updateReviewControls(newStatus);

    showActionFeedback(
      isApprove
        ? "Retailer application approved. The retailer has been notified."
        : "Retailer application rejected. The retailer has been notified.",
      "success"
    );
  } catch (error) {
    console.error(`${decision} failed:`, error);
    showActionFeedback(
      error?.message || "Something went wrong. Please try again.",
      "danger"
    );
  } finally {
    delete triggeringBtn.dataset.pending;
    setButtonsLoading(false);
  }
}

approveBtn.addEventListener("click", () => handleDecision("approve"));
rejectBtn.addEventListener("click", () => handleDecision("reject"));

// ---------- Page init ----------

guardAdminPage().then(({ user }) => {
  emailEl.textContent = user.email;
  nameEl.textContent = "Administrator";
  avatarEl.textContent = initials(user.email);
  loadRetailer();
});

logoutBtn.addEventListener("click", async () => {
  logoutBtn.disabled = true;
  try {
    await logoutAdmin();
  } catch (error) {
    console.error("Logout failed:", error);
    logoutBtn.disabled = false;
  }
});
