// ============================================================
// FaceFit AI — Users (read-only)
// ------------------------------------------------------------
// Reads existing users/ documents. Fully read-only — no create/
// update/delete anywhere in this file, matching the original
// brief ("view user details... do not invent user-management
// actions"). Never reads or displays Password_Hash — that field
// is never populated in Firestore in the first place.
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

const searchInput = document.getElementById("search-input");
const tbody = document.getElementById("users-tbody");
const loadingState = document.getElementById("loading-state");
const emptyState = document.getElementById("empty-state");
const errorState = document.getElementById("error-state");

let allUsers = [];

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
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

function fullName(user) {
  return [user.firstName, user.lastName].filter(Boolean).join(" ") || "—";
}

function renderRows(users) {
  tbody.innerHTML = "";

  users.forEach((user) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="ps-4 fw-semibold">${escapeHtml(fullName(user))}</td>
      <td class="text-muted-ff">${escapeHtml(user.emailAddress)}</td>
      <td>${escapeHtml(user.role)}</td>
      <td>${escapeHtml(user.accountStatus)}</td>
      <td class="text-muted-ff">${formatDate(user.createdAt)}</td>
      <td class="pe-4">
        <a href="user-detail.html?id=${encodeURIComponent(user.id)}" class="btn btn-sm btn-outline-secondary">
          View
        </a>
      </td>
    `;
    tbody.appendChild(tr);
  });

  emptyState.classList.toggle("d-none", users.length > 0);
}

function applySearchAndRender() {
  const term = searchInput.value.trim().toLowerCase();

  const filtered = term
    ? allUsers.filter((u) =>
        fullName(u).toLowerCase().includes(term) ||
        (u.emailAddress || "").toLowerCase().includes(term)
      )
    : allUsers;

  renderRows(filtered);
}

async function loadUsers() {
  loadingState.classList.remove("d-none");
  errorState.classList.add("d-none");
  emptyState.classList.add("d-none");

  try {
    const usersQuery = query(collection(db, "users"), orderBy("createdAt", "desc"));
    const snapshot = await getDocs(usersQuery);

    allUsers = snapshot.docs.map((docSnap) => ({
      id: docSnap.id,
      ...docSnap.data()
    }));

    applySearchAndRender();
  } catch (error) {
    console.error("Failed to load users:", error);
    errorState.textContent =
      "Unable to load users right now. Please refresh the page or try again shortly.";
    errorState.classList.remove("d-none");
  } finally {
    loadingState.classList.add("d-none");
  }
}

searchInput.addEventListener("input", applySearchAndRender);

guardAdminPage().then(({ user }) => {
  emailEl.textContent = user.email;
  nameEl.textContent = "Administrator";
  avatarEl.textContent = initials(user.email);
  loadUsers();
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
