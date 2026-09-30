// ============================================================
// FaceFit AI — Dashboard Page Script
// ------------------------------------------------------------
// Read-only. Computes retailer application counts (total/pending/
// approved/rejected) and a pending-applications queue directly
// from the existing retailers collection. Never creates, updates,
// or deletes anything. Frame Listings / Users stay honestly
// labeled "Coming Soon" — no fake data for unbuilt modules.
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

const metricsLoading = document.getElementById("metrics-loading");
const metricsError = document.getElementById("metrics-error");
const metricsContent = document.getElementById("metrics-content");

const countTotal = document.getElementById("count-total");
const countPending = document.getElementById("count-pending");
const countApproved = document.getElementById("count-approved");
const countRejected = document.getElementById("count-rejected");

const pendingTbody = document.getElementById("pending-tbody");
const pendingEmpty = document.getElementById("pending-empty");

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

// ---------- Rendering helpers (same conventions as retailers.js) ----------

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

function renderPendingRows(pendingRetailers) {
  pendingTbody.innerHTML = "";

  pendingRetailers.forEach((retailer) => {
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
    pendingTbody.appendChild(tr);
  });

  pendingEmpty.classList.toggle("d-none", pendingRetailers.length > 0);
}

// ---------- Data loading ----------

async function loadDashboardData() {
  metricsLoading.classList.remove("d-none");
  metricsError.classList.add("d-none");
  metricsContent.classList.add("d-none");

  try {
    const retailersQuery = query(collection(db, "retailers"), orderBy("createdAt", "desc"));
    const snapshot = await getDocs(retailersQuery);

    const retailers = snapshot.docs.map((docSnap) => ({
      id: docSnap.id,
      ...docSnap.data()
    }));

    const total = retailers.length;
    const pending = retailers.filter((r) => r.status === "pending");
    const approved = retailers.filter((r) => r.status === "approved");
    const rejected = retailers.filter((r) => r.status === "rejected");

    countTotal.textContent = total;
    countPending.textContent = pending.length;
    countApproved.textContent = approved.length;
    countRejected.textContent = rejected.length;

    renderPendingRows(pending);

    metricsContent.classList.remove("d-none");
  } catch (error) {
    console.error("Failed to load dashboard data:", error);
    metricsError.textContent =
      "Unable to load retailer application data right now. Please refresh the page or try again shortly.";
    metricsError.classList.remove("d-none");
  } finally {
    metricsLoading.classList.add("d-none");
  }
}

// ---------- Page init ----------

guardAdminPage().then(({ user }) => {
  emailEl.textContent = user.email;
  nameEl.textContent = "Administrator";
  avatarEl.textContent = initials(user.email);
  loadDashboardData();
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
