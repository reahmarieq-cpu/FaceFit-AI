// ============================================================
// FaceFit AI — Dashboard
// ------------------------------------------------------------
// Read-only. Every number is derived from real Firestore data:
//   retailers -> pending applications, approved shops, the queue
//   frames    -> frames to review, validated frames
//   users     -> registered users, active users
// Nothing is written. If frames/users can't be read, those tiles
// show "—" instead of fake data; only a retailers failure shows
// the page-level error (it is the primary dataset, as before).
// ============================================================

import { escapeHtml, formatDate, initialsOf, onSearch } from "./admin-layout.js";
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

const pendingList = document.getElementById("pending-list");
const pendingEmpty = document.getElementById("pending-empty");

const QUEUE_LIMIT = 5;
let pendingRetailers = [];
let searchTerm = "";

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

function setText(id, value) {
  document.getElementById(id).textContent = value;
}

function renderQueue() {
  const matches = pendingRetailers.filter((r) => {
    if (!searchTerm) return true;
    return [r.retailName, r.retailAddress, r.contactPerson]
      .some((v) => String(v || "").toLowerCase().includes(searchTerm));
  });

  pendingList.innerHTML = "";
  matches.slice(0, QUEUE_LIMIT).forEach((r) => {
    const href = `retailer-detail.html?id=${encodeURIComponent(r.id)}`;
    const row = document.createElement("div");
    row.className = "queue-row";
    row.innerHTML = `
      <div class="entity">
        <span class="avatar-soft">${escapeHtml(initialsOf(r.retailName))}</span>
        <div>
          <a href="${href}" class="entity-title">${escapeHtml(r.retailName)}</a>
          <div class="entity-sub">${escapeHtml(r.retailAddress)} · ${formatDate(r.createdAt)}</div>
        </div>
      </div>
      <a href="${href}" class="btn-soft btn-soft-teal"><i class="bi bi-eye"></i> Review</a>
    `;
    pendingList.appendChild(row);
  });

  pendingEmpty.classList.toggle("d-none", matches.length > 0);
}

async function loadDashboardData() {
  metricsLoading.classList.remove("d-none");
  metricsError.classList.add("d-none");
  metricsContent.classList.add("d-none");

  try {
    const [retailersRes, framesRes, usersRes] = await Promise.allSettled([
      getDocs(query(collection(db, "retailers"), orderBy("createdAt", "desc"))),
      getDocs(collection(db, "frames")),
      getDocs(collection(db, "users"))
    ]);

    if (retailersRes.status === "rejected") throw retailersRes.reason;

    const retailers = retailersRes.value.docs.map((d) => ({ id: d.id, ...d.data() }));
    pendingRetailers = retailers.filter((r) => r.status === "pending");
    const approvedShops = retailers.filter((r) => r.status === "approved").length;

    setText("stat-pending-apps", pendingRetailers.length);
    setText("stat-approved-shops", approvedShops);
    document.getElementById("chip-pending-apps").classList.toggle("d-none", pendingRetailers.length === 0);

    // Frames (optional section)
    if (framesRes.status === "fulfilled") {
      const frames = framesRes.value.docs.map((d) => d.data());
      const pendingFrames = frames.filter((f) => f.status === "pending").length;
      const validated = frames.filter((f) => f.status === "approved").length;
      setText("stat-frames-review", pendingFrames);
      setText("health-awaiting", pendingFrames);
      setText("health-validated", validated);
      document.getElementById("chip-frames-review").classList.toggle("d-none", pendingFrames === 0);
    } else {
      console.error("Dashboard: could not read frames:", framesRes.reason);
      ["stat-frames-review", "health-awaiting", "health-validated"].forEach((id) => setText(id, "—"));
    }

    // Users (optional section)
    if (usersRes.status === "fulfilled") {
      const users = usersRes.value.docs.map((d) => d.data());
      const active = users.filter((u) => String(u.accountStatus || "").toLowerCase() === "active").length;
      setText("stat-users", users.length);
      setText("health-total-users", users.length);
      setText("health-active-users", active);
    } else {
      console.error("Dashboard: could not read users:", usersRes.reason);
      ["stat-users", "health-total-users", "health-active-users"].forEach((id) => setText(id, "—"));
    }

    renderQueue();
    metricsContent.classList.remove("d-none");
  } catch (error) {
    console.error("Failed to load dashboard data:", error);
    metricsError.textContent =
      "Unable to load dashboard data right now. Please refresh the page or try again shortly.";
    metricsError.classList.remove("d-none");
  } finally {
    metricsLoading.classList.add("d-none");
  }
}

onSearch((term) => {
  searchTerm = term;
  renderQueue();
});

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
