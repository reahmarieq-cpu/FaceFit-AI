// ============================================================
// FaceFit AI — Frame Listings (read-only)
// ------------------------------------------------------------
// Reads existing frames/ documents from Firestore. Read-only —
// no create/update/delete anywhere in this file. Status values
// (pending/approved/hidden) are PROPOSED, not yet confirmed —
// this file is deliberately not writing anything that depends
// on them being final. Validate/Hide actions and the detail page
// are intentionally NOT built here; those need a trusted Cloud
// Function, same as retailer approval, and require separate
// explicit approval before being built.
// ============================================================

import { guardAdminPage } from "./admin-guard.js";
import { logoutAdmin } from "./auth.js";
import { db } from "./firebase-config.js";
import {
  collection,
  getDocs,
  orderBy,
  query
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

const nameEl = document.getElementById("admin-name");
const emailEl = document.getElementById("admin-email");
const avatarEl = document.getElementById("admin-avatar");
const logoutBtn = document.getElementById("logout-btn");

const filterBar = document.getElementById("filter-bar");
const tbody = document.getElementById("frames-tbody");
const loadingState = document.getElementById("loading-state");
const emptyState = document.getElementById("empty-state");
const errorState = document.getElementById("error-state");

let allFrames = [];
let activeFilter = "all";

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

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
  return timestamp.toDate().toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric"
  });
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value ?? "";
  return div.innerHTML;
}

function renderRows(frames) {
  tbody.innerHTML = "";

  frames.forEach((frame) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="ps-4 fw-semibold">${escapeHtml(frame.brand)}</td>
      <td>${escapeHtml(frame.model)}</td>
      <td class="text-muted-ff">${escapeHtml(frame.type)}</td>
      <td><span class="${statusBadgeClass(frame.status)}">${statusLabel(frame.status)}</span></td>
      <td class="text-muted-ff">${formatDate(frame.createdAt)}</td>
      <td class="pe-4">
        <a href="frame-listing-detail.html?id=${encodeURIComponent(frame.id)}" class="btn btn-sm btn-outline-secondary">
          View
        </a>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

function applyFilterAndRender() {
  const filtered =
    activeFilter === "all" ? allFrames : allFrames.filter((f) => f.status === activeFilter);

  renderRows(filtered);
  emptyState.classList.toggle("d-none", filtered.length > 0);
}

async function loadFrames() {
  loadingState.classList.remove("d-none");
  errorState.classList.add("d-none");
  emptyState.classList.add("d-none");

  try {
    const framesQuery = query(collection(db, "frames"), orderBy("createdAt", "desc"));
    const snapshot = await getDocs(framesQuery);

    allFrames = snapshot.docs.map((docSnap) => ({
      id: docSnap.id,
      ...docSnap.data()
    }));

    applyFilterAndRender();
  } catch (error) {
    console.error("Failed to load frame listings:", error);
    errorState.textContent =
      "Unable to load frame listings right now. Please refresh the page or try again shortly.";
    errorState.classList.remove("d-none");
  } finally {
    loadingState.classList.add("d-none");
  }
}

filterBar.addEventListener("click", (event) => {
  const button = event.target.closest(".filter-pill");
  if (!button) return;

  filterBar.querySelectorAll(".filter-pill").forEach((pill) => pill.classList.remove("active"));
  button.classList.add("active");

  activeFilter = button.dataset.filter;
  applyFilterAndRender();
});

guardAdminPage().then(({ user }) => {
  emailEl.textContent = user.email;
  nameEl.textContent = "Administrator";
  avatarEl.textContent = initials(user.email);
  loadFrames();
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
