// ============================================================
// FaceFit AI — Shop Applications (retailers collection)
// ------------------------------------------------------------
// Read-only list. Same Firestore query as before
// (retailers, orderBy createdAt desc, filtered client-side).
// Approve/Reject still happen ONLY on retailer-detail.html via
// the approveRetailer / rejectRetailer Cloud Functions — the
// "Decision" column here just routes pending rows to that page.
// ============================================================

import { escapeHtml, formatDate, initialsOf, statusBadge, onSearch } from "./admin-layout.js";
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

let allRetailers = [];
let activeFilter = "all";
let searchTerm = "";

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

function matchesSearch(r) {
  if (!searchTerm) return true;
  return [r.retailName, r.contactPerson, r.permit, r.emailAddress, r.retailAddress]
    .some((v) => String(v || "").toLowerCase().includes(searchTerm));
}

function renderRows(retailers) {
  tbody.innerHTML = "";

  retailers.forEach((r) => {
    const detailHref = `retailer-detail.html?id=${encodeURIComponent(r.id)}`;
    const decision =
      r.status === "pending"
        ? `<a href="${detailHref}" class="btn-soft btn-soft-teal"><i class="bi bi-eye"></i> Review</a>`
        : `<span class="text-muted-ff" style="font-size:0.8rem;">Reviewed</span>`;

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="cell-primary">
        <div class="entity">
          <span class="avatar-soft">${escapeHtml(initialsOf(r.retailName))}</span>
          <div>
            <a href="${detailHref}" class="entity-title">${escapeHtml(r.retailName)}</a>
            <div class="entity-sub">${escapeHtml(r.retailAddress)}</div>
          </div>
        </div>
      </td>
      <td data-label="Contact">${escapeHtml(r.contactPerson)}</td>
      <td data-label="Permit No." class="fw-bold" style="font-size:0.82rem;">${escapeHtml(r.permit)}</td>
      <td data-label="Submitted" class="text-muted-ff">${formatDate(r.createdAt)}</td>
      <td data-label="Status">${statusBadge(r.status)}</td>
      <td data-label="Decision" class="td-end">${decision}</td>
    `;
    tbody.appendChild(tr);
  });
}

function updateCounts() {
  const count = (s) => allRetailers.filter((r) => r.status === s).length;
  document.getElementById("count-all").textContent = allRetailers.length;
  document.getElementById("count-pending").textContent = count("pending");
  document.getElementById("count-approved").textContent = count("approved");
  document.getElementById("count-rejected").textContent = count("rejected");
}

function applyFilterAndRender() {
  const filtered = allRetailers
    .filter((r) => activeFilter === "all" || r.status === activeFilter)
    .filter(matchesSearch);

  renderRows(filtered);
  emptyState.classList.toggle("d-none", filtered.length > 0 || !errorState.classList.contains("d-none"));
}

async function loadRetailers() {
  loadingState.classList.remove("d-none");
  errorState.classList.add("d-none");
  emptyState.classList.add("d-none");

  try {
    const retailersQuery = query(collection(db, "retailers"), orderBy("createdAt", "desc"));
    const snapshot = await getDocs(retailersQuery);

    allRetailers = snapshot.docs.map((docSnap) => ({ id: docSnap.id, ...docSnap.data() }));

    updateCounts();
    applyFilterAndRender();
  } catch (error) {
    console.error("Failed to load shop applications:", error);
    errorState.textContent =
      "Unable to load shop applications right now. Please refresh the page or try again shortly.";
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

onSearch((term) => {
  searchTerm = term;
  applyFilterAndRender();
});

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
