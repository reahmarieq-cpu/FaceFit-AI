// ============================================================
// FaceFit AI — Frame Catalog
// ------------------------------------------------------------
// Reads the SAME frames/ collection as Frame Listings, filtered
// to status == "approved" — no separate catalog collection
// exists, per the single FRAME entity in the ERD. The only write
// action here (Hide) goes through the same hideFrame Cloud
// Function used on the Frame Listing detail page — never a
// direct Firestore write.
// ============================================================

import { guardAdminPage } from "./admin-guard.js";
import { logoutAdmin } from "./auth.js";
import { db, functions } from "./firebase-config.js";
import {
  collection,
  getDocs,
  query,
  where
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
import { httpsCallable } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-functions.js";

const nameEl = document.getElementById("admin-name");
const emailEl = document.getElementById("admin-email");
const avatarEl = document.getElementById("admin-avatar");
const logoutBtn = document.getElementById("logout-btn");

const searchInput = document.getElementById("search-input");
const tbody = document.getElementById("catalog-tbody");
const loadingState = document.getElementById("loading-state");
const emptyState = document.getElementById("empty-state");
const errorState = document.getElementById("error-state");
const actionFeedback = document.getElementById("action-feedback");

const hideFrameFn = httpsCallable(functions, "hideFrame");

let allFrames = [];

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value ?? "";
  return div.innerHTML;
}

function showActionFeedback(message, type) {
  actionFeedback.textContent = message;
  actionFeedback.className = `alert alert-${type} mt-3`;
  actionFeedback.classList.remove("d-none");
}

function renderRows(frames) {
  tbody.innerHTML = "";

  frames.forEach((frame) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="ps-4 fw-semibold">${escapeHtml(frame.brand)}</td>
      <td>${escapeHtml(frame.model)}</td>
      <td class="text-muted-ff">${escapeHtml(frame.type)}</td>
      <td class="text-muted-ff">${escapeHtml(frame.material)}</td>
      <td><span class="badge-status badge-status-approved">Approved</span></td>
      <td class="pe-4">
        <button class="btn btn-sm btn-outline-secondary hide-btn" data-frame-id="${escapeHtml(frame.id)}">
          Hide
        </button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  emptyState.classList.toggle("d-none", frames.length > 0);
}

function applySearchAndRender() {
  const term = searchInput.value.trim().toLowerCase();

  const filtered = term
    ? allFrames.filter((f) =>
        (f.brand || "").toLowerCase().includes(term) ||
        (f.model || "").toLowerCase().includes(term)
      )
    : allFrames;

  renderRows(filtered);
}

async function loadCatalog() {
  loadingState.classList.remove("d-none");
  errorState.classList.add("d-none");
  emptyState.classList.add("d-none");

  try {
    const catalogQuery = query(collection(db, "frames"), where("status", "==", "approved"));
    const snapshot = await getDocs(catalogQuery);

    allFrames = snapshot.docs.map((docSnap) => ({
      id: docSnap.id,
      ...docSnap.data()
    }));

    applySearchAndRender();
  } catch (error) {
    console.error("Failed to load frame catalog:", error);
    errorState.textContent =
      "Unable to load the frame catalog right now. Please refresh the page or try again shortly.";
    errorState.classList.remove("d-none");
  } finally {
    loadingState.classList.add("d-none");
  }
}

tbody.addEventListener("click", async (event) => {
  const button = event.target.closest(".hide-btn");
  if (!button) return;

  const frameId = button.dataset.frameId;
  button.disabled = true;
  button.textContent = "Hiding…";
  actionFeedback.classList.add("d-none");

  try {
    await hideFrameFn({ frameId });
    allFrames = allFrames.filter((f) => f.id !== frameId);
    applySearchAndRender();
    showActionFeedback("Frame hidden and removed from the catalog view.", "success");
  } catch (error) {
    console.error("Hide failed:", error);
    showActionFeedback(error?.message || "Something went wrong. Please try again.", "danger");
    button.disabled = false;
    button.textContent = "Hide";
  }
});

searchInput.addEventListener("input", applySearchAndRender);

guardAdminPage().then(({ user }) => {
  emailEl.textContent = user.email;
  nameEl.textContent = "Administrator";
  avatarEl.textContent = initials(user.email);
  loadCatalog();
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
