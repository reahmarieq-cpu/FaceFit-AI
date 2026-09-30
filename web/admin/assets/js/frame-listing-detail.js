// ============================================================
// FaceFit AI — Frame Listing Detail / Review
// ------------------------------------------------------------
// Reads a single frames/{id} document for display. Approve/Hide
// NEVER write to Firestore directly — both go through the
// validateFrame / hideFrame Cloud Functions, mirroring the
// retailer-detail.js pattern exactly.
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
const validateBtn = document.getElementById("validate-btn");
const hideBtn = document.getElementById("hide-btn");
const actionFeedback = document.getElementById("action-feedback");
const alreadyReviewedNote = document.getElementById("already-reviewed-note");

const frameImageEl = document.getElementById("field-frameImage");
const frameImageEmptyEl = document.getElementById("field-frameImage-empty");

const validateFrameFn = httpsCallable(functions, "validateFrame");
const hideFrameFn = httpsCallable(functions, "hideFrame");

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

const frameId = new URLSearchParams(window.location.search).get("id");

function statusBadgeClass(status) {
  if (status === "approved") return "badge-status badge-status-approved";
  if (status === "hidden") return "badge-status badge-status-hidden";
  return "badge-status badge-status-pending";
}

function statusLabel(status) {
  if (status === "approved") return "Approved";
  if (status === "hidden") return "Hidden";
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

function renderFrame(frame) {
  setText("field-brand", frame.brand);
  setText("field-model", frame.model);
  setText("field-type", frame.type);
  setText("field-shape", frame.shape);
  setText("field-material", frame.material);
  setText("field-color", frame.color);
  setText("field-retailId", frame.retailId);
  setText("field-createdAt", formatDate(frame.createdAt));

  if (frame.frameImage) {
    frameImageEl.src = frame.frameImage;
    frameImageEl.classList.remove("d-none");
    frameImageEmptyEl.classList.add("d-none");
  } else {
    frameImageEl.classList.add("d-none");
    frameImageEmptyEl.classList.remove("d-none");
  }

  statusBadge.className = statusBadgeClass(frame.status);
  statusBadge.textContent = statusLabel(frame.status);

  updateReviewControls(frame.status);
}

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
  validateBtn.disabled = isLoading;
  hideBtn.disabled = isLoading;
  validateBtn.textContent = isLoading && validateBtn.dataset.pending ? "Approving…" : "Approve";
  hideBtn.textContent = isLoading && hideBtn.dataset.pending ? "Hiding…" : "Hide";
}

async function loadFrame() {
  if (!frameId) {
    loadingState.classList.add("d-none");
    errorState.textContent = "No frame ID was provided in the URL.";
    errorState.classList.remove("d-none");
    return;
  }

  try {
    const snap = await getDoc(doc(db, "frames", frameId));

    if (!snap.exists()) {
      loadingState.classList.add("d-none");
      errorState.textContent = `No frame listing found with ID "${frameId}".`;
      errorState.classList.remove("d-none");
      return;
    }

    renderFrame(snap.data());

    loadingState.classList.add("d-none");
    detailContent.classList.remove("d-none");
  } catch (error) {
    console.error("Failed to load frame listing:", error);
    loadingState.classList.add("d-none");
    errorState.textContent =
      "Unable to load this frame listing. You may not have permission, or it may not exist.";
    errorState.classList.remove("d-none");
  }
}

async function handleDecision(decision) {
  const isValidate = decision === "validate";
  const fn = isValidate ? validateFrameFn : hideFrameFn;
  const triggeringBtn = isValidate ? validateBtn : hideBtn;

  triggeringBtn.dataset.pending = "true";
  setButtonsLoading(true);
  actionFeedback.classList.add("d-none");

  try {
    const result = await fn({ frameId });
    const newStatus = result?.data?.status || (isValidate ? "approved" : "hidden");

    statusBadge.className = statusBadgeClass(newStatus);
    statusBadge.textContent = statusLabel(newStatus);
    updateReviewControls(newStatus);

    showActionFeedback(
      isValidate
        ? "Frame listing approved. It is now part of the catalog."
        : "Frame listing hidden. It is no longer visible.",
      "success"
    );
  } catch (error) {
    console.error(`${decision} failed:`, error);
    showActionFeedback(error?.message || "Something went wrong. Please try again.", "danger");
  } finally {
    delete triggeringBtn.dataset.pending;
    setButtonsLoading(false);
  }
}

validateBtn.addEventListener("click", () => handleDecision("validate"));
hideBtn.addEventListener("click", () => handleDecision("hide"));

guardAdminPage().then(({ user }) => {
  emailEl.textContent = user.email;
  nameEl.textContent = "Administrator";
  avatarEl.textContent = initials(user.email);
  loadFrame();
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
