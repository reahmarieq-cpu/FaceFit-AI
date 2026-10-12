// ============================================================
// FaceFit AI — Frame Catalog
// ------------------------------------------------------------
// Same behaviour as before: reads the frames collection filtered
// to status == "approved" (no separate catalog collection exists)
// and hides a frame ONLY through the hideFrame Cloud Function —
// never a direct Firestore write. The UI shows "Valid" for the
// stored value "approved". Shop names come from a read-only
// lookup of retailers/{retailId}.
// ============================================================

import { escapeHtml, frameIconHtml, statusBadge, onSearch } from "./admin-layout.js";
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

const tbody = document.getElementById("catalog-tbody");
const heading = document.getElementById("catalog-heading");
const loadingState = document.getElementById("loading-state");
const emptyState = document.getElementById("empty-state");
const errorState = document.getElementById("error-state");
const actionFeedback = document.getElementById("action-feedback");

const hideFrameFn = httpsCallable(functions, "hideFrame");

let allFrames = [];
let shopNames = {};
let searchTerm = "";

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

const shopOf = (f) => shopNames[f.retailId] || f.retailId || "—";
const titleOf = (f) => [f.brand, f.model].filter(Boolean).join(" ") || "—";

function showActionFeedback(message, type) {
  actionFeedback.textContent = message;
  actionFeedback.className = `alert alert-${type}`;
}

function updateHeading() {
  const n = allFrames.length;
  heading.textContent = `${n} validated frame${n === 1 ? "" : "s"} across all shops`;
}

function renderRows(frames) {
  tbody.innerHTML = "";

  frames.forEach((f) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="cell-primary">
        <div class="entity">
          ${frameIconHtml(f)}
          <div>
            <span class="entity-title">${escapeHtml(titleOf(f))}</span>
            <div class="entity-sub">${escapeHtml(f.color)}</div>
          </div>
        </div>
      </td>
      <td data-label="Shop" class="fw-semibold">${escapeHtml(shopOf(f))}</td>
      <td data-label="Shape">${escapeHtml(f.shape)}</td>
      <td data-label="Status">${statusBadge(f.status, { approved: "Valid" })}</td>
      <td data-label="Actions" class="td-end">
        <button class="btn-icon hide-btn" data-frame-id="${escapeHtml(f.id)}"
                title="Hide from catalog" aria-label="Hide ${escapeHtml(titleOf(f))} from catalog">
          <i class="bi bi-eye-slash"></i>
        </button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  emptyState.classList.toggle("d-none", frames.length > 0 || !errorState.classList.contains("d-none"));
}

function applySearchAndRender() {
  const filtered = searchTerm
    ? allFrames.filter((f) =>
        [f.brand, f.model, f.shape, f.color, shopOf(f)]
          .some((v) => String(v || "").toLowerCase().includes(searchTerm))
      )
    : allFrames;

  renderRows(filtered);
}

async function loadShopNames() {
  try {
    const snap = await getDocs(collection(db, "retailers"));
    snap.docs.forEach((d) => { shopNames[d.id] = d.data().retailName; });
  } catch (error) {
    console.warn("Frame Catalog: could not resolve shop names:", error?.code || error);
  }
}

async function loadCatalog() {
  loadingState.classList.remove("d-none");
  errorState.classList.add("d-none");
  emptyState.classList.add("d-none");

  try {
    const catalogQuery = query(collection(db, "frames"), where("status", "==", "approved"));
    const [snapshot] = await Promise.all([getDocs(catalogQuery), loadShopNames()]);

    allFrames = snapshot.docs.map((docSnap) => ({ id: docSnap.id, ...docSnap.data() }));

    updateHeading();
    applySearchAndRender();
  } catch (error) {
    console.error("Failed to load frame catalog:", error);
    heading.textContent = "Frame catalog unavailable";
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

  if (!window.confirm("Hide this frame? It will no longer appear in the catalog.")) return;

  const frameId = button.dataset.frameId;
  button.disabled = true;
  actionFeedback.classList.add("d-none");

  try {
    await hideFrameFn({ frameId });
    allFrames = allFrames.filter((f) => f.id !== frameId);
    updateHeading();
    applySearchAndRender();
    showActionFeedback("Frame hidden and removed from the catalog view.", "success");
  } catch (error) {
    console.error("Hide failed:", error);
    showActionFeedback(error?.message || "Something went wrong. Please try again.", "danger");
    button.disabled = false;
  }
});

onSearch((term) => {
  searchTerm = term;
  applySearchAndRender();
});

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
