// ============================================================
// FaceFit AI — Frame Review (frames collection)
// ------------------------------------------------------------
// Read-only list. Same Firestore query as before
// (frames, orderBy createdAt desc, filtered client-side).
// Validate / Hide still happen ONLY on frame-listing-detail.html
// through the validateFrame / hideFrame Cloud Functions.
// The shop name is resolved with a read-only lookup of
// retailers/{retailId} (frames store retailId only, per schema).
// ============================================================

import { escapeHtml, frameIconHtml, statusBadge, onSearch } from "./admin-layout.js";
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
let shopNames = {};          // retailId -> retailName
let activeFilter = "pending";
let searchTerm = "";

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

const shopOf = (f) => shopNames[f.retailId] || f.retailId || "—";
const titleOf = (f) => [f.brand, f.model].filter(Boolean).join(" ") || "—";
const subOf = (f) => [f.shape, f.material].filter(Boolean).join(" · ");

function matchesSearch(f) {
  if (!searchTerm) return true;
  return [f.brand, f.model, f.shape, f.material, shopOf(f)]
    .some((v) => String(v || "").toLowerCase().includes(searchTerm));
}

function renderRows(frames) {
  tbody.innerHTML = "";

  frames.forEach((f) => {
    const detailHref = `frame-listing-detail.html?id=${encodeURIComponent(f.id)}`;
    const moderate =
      f.status === "pending"
        ? `<a href="${detailHref}" class="btn-soft btn-soft-teal"><i class="bi bi-eye"></i> Review</a>`
        : `<span class="text-muted-ff" style="font-size:0.8rem;">Reviewed</span>`;

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="cell-primary">
        <div class="entity">
          ${frameIconHtml(f)}
          <div>
            <a href="${detailHref}" class="entity-title">${escapeHtml(titleOf(f))}</a>
            <div class="entity-sub">${escapeHtml(subOf(f))}</div>
          </div>
        </div>
      </td>
      <td data-label="Shop" class="fw-semibold">${escapeHtml(shopOf(f))}</td>
      <td data-label="Status">${statusBadge(f.status, { approved: "Valid" })}</td>
      <td data-label="Moderate" class="td-end">${moderate}</td>
    `;
    tbody.appendChild(tr);
  });
}

function updateCounts() {
  const count = (s) => allFrames.filter((f) => f.status === s).length;
  document.getElementById("count-all").textContent = allFrames.length;
  document.getElementById("count-pending").textContent = count("pending");
  document.getElementById("count-approved").textContent = count("approved");
  document.getElementById("count-hidden").textContent = count("hidden");
}

function applyFilterAndRender() {
  const filtered = allFrames
    .filter((f) => activeFilter === "all" || f.status === activeFilter)
    .filter(matchesSearch);

  renderRows(filtered);
  emptyState.classList.toggle("d-none", filtered.length > 0 || !errorState.classList.contains("d-none"));
}

async function loadShopNames() {
  // Read-only helper; a failure just falls back to showing the retailId.
  try {
    const snap = await getDocs(collection(db, "retailers"));
    snap.docs.forEach((d) => { shopNames[d.id] = d.data().retailName; });
  } catch (error) {
    console.warn("Frame Review: could not resolve shop names:", error?.code || error);
  }
}

async function loadFrames() {
  loadingState.classList.remove("d-none");
  errorState.classList.add("d-none");
  emptyState.classList.add("d-none");

  try {
    const framesQuery = query(collection(db, "frames"), orderBy("createdAt", "desc"));
    const [snapshot] = await Promise.all([getDocs(framesQuery), loadShopNames()]);

    allFrames = snapshot.docs.map((docSnap) => ({ id: docSnap.id, ...docSnap.data() }));

    updateCounts();
    applyFilterAndRender();
  } catch (error) {
    console.error("Failed to load frame submissions:", error);
    errorState.textContent =
      "Unable to load frame submissions right now. Please refresh the page or try again shortly.";
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
