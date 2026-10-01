// ============================================================
// FaceFit AI — Retailer Applications List
// ------------------------------------------------------------
// Reads existing retailers/ documents from Firestore. Never
// creates or modifies documents — this page is read-only.
// Approve/Reject happen only on retailer-detail.html.
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
const tbody = document.getElementById("retailers-tbody");
const loadingState = document.getElementById("loading-state");
const emptyState = document.getElementById("empty-state");
const errorState = document.getElementById("error-state");

let allRetailers = [];   // full list, fetched once
let activeFilter = "all";

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

// ---------- Rendering helpers ----------

function statusBadgeClass(status) {
  if (status === "approved") return "badge-status badge-status-approved";
  if (status === "rejected") return "badge-status badge-status-rejected";
  return "badge-status badge-status-pending"; // default/fallback
}

function statusLabel(status) {
  if (status === "approved") return "Approved";
  if (status === "rejected") return "Rejected";
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

function renderRows(retailers) {
  tbody.innerHTML = "";

  retailers.forEach((retailer) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="ps-4 fw-semibold">${escapeHtml(retailer.retailName)}</td>
      <td>${escapeHtml(retailer.contactPerson)}</td>
      <td class="text-muted-ff">${escapeHtml(retailer.emailAddress)}</td>
      <td><span class="${statusBadgeClass(retailer.status)}">${statusLabel(retailer.status)}</span></td>
      <td class="text-muted-ff">${formatDate(retailer.createdAt)}</td>
      <td class="pe-4">
        <a href="retailer-detail.html?id=${encodeURIComponent(retailer.id)}" class="btn btn-sm btn-outline-secondary">
          View
        </a>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

function applyFilterAndRender() {
  const filtered =
    activeFilter === "all"
      ? allRetailers
      : allRetailers.filter((r) => r.status === activeFilter);

  renderRows(filtered);
  emptyState.classList.toggle("d-none", filtered.length > 0);
}

// ---------- Data loading ----------

async function loadRetailers() {
  loadingState.classList.remove("d-none");
  errorState.classList.add("d-none");
  emptyState.classList.add("d-none");

  try {
    const retailersQuery = query(collection(db, "retailers"), orderBy("createdAt", "desc"));
    const snapshot = await getDocs(retailersQuery);

    allRetailers = snapshot.docs.map((docSnap) => ({
      id: docSnap.id,
      ...docSnap.data()
    }));

    applyFilterAndRender();
  } catch (error) {
    console.error("Failed to load retailer applications:", error);
    errorState.textContent =
      "Unable to load retailer applications right now. Please refresh the page or try again shortly.";
    errorState.classList.remove("d-none");
  } finally {
    loadingState.classList.add("d-none");
  }
}

// ---------- Filter bar wiring ----------

filterBar.addEventListener("click", (event) => {
  const button = event.target.closest(".filter-pill");
  if (!button) return;

  filterBar.querySelectorAll(".filter-pill").forEach((pill) => pill.classList.remove("active"));
  button.classList.add("active");

  activeFilter = button.dataset.filter;
  applyFilterAndRender();
});

// ---------- Page init ----------

guardAdminPage().then(({ user }) => {
  emailEl.textContent = user.email;
  nameEl.textContent = "Administrator";
  avatarEl.textContent = initials(user.email);
  loadRetailers();
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
