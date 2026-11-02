// ============================================================
// FaceFit AI — User Detail (read-only)
// ------------------------------------------------------------
// Reads a single users/{id} document. No writes anywhere in
// this file. Never reads/displays Password_Hash.
// ============================================================

import { guardAdminPage } from "./admin-guard.js";
import { logoutAdmin } from "./auth.js";
import { db } from "./firebase-config.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

const nameEl = document.getElementById("admin-name");
const emailEl = document.getElementById("admin-email");
const avatarEl = document.getElementById("admin-avatar");
const logoutBtn = document.getElementById("logout-btn");

const loadingState = document.getElementById("loading-state");
const errorState = document.getElementById("error-state");
const detailContent = document.getElementById("detail-content");

function initials(email) {
  return email ? email.slice(0, 2).toUpperCase() : "AD";
}

const userId = new URLSearchParams(window.location.search).get("id");

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

function renderUser(user) {
  setText("field-firstName", user.firstName);
  setText("field-lastName", user.lastName);
  setText("field-emailAddress", user.emailAddress);
  setText("field-contactNumber", user.contactNumber);
  setText("field-role", user.role);
  setText("field-accountStatus", user.accountStatus);
  setText("field-createdAt", formatDate(user.createdAt));
}

async function loadUser() {
  if (!userId) {
    loadingState.classList.add("d-none");
    errorState.textContent = "No user ID was provided in the URL.";
    errorState.classList.remove("d-none");
    return;
  }

  try {
    const snap = await getDoc(doc(db, "users", userId));

    if (!snap.exists()) {
      loadingState.classList.add("d-none");
      errorState.textContent = `No user found with ID "${userId}".`;
      errorState.classList.remove("d-none");
      return;
    }

    renderUser(snap.data());

    loadingState.classList.add("d-none");
    detailContent.classList.remove("d-none");
  } catch (error) {
    console.error("Failed to load user:", error);
    loadingState.classList.add("d-none");
    errorState.textContent =
      "Unable to load this user. You may not have permission, or it may not exist.";
    errorState.classList.remove("d-none");
  }
}

guardAdminPage().then(({ user }) => {
  emailEl.textContent = user.email;
  nameEl.textContent = "Administrator";
  avatarEl.textContent = initials(user.email);
  loadUser();
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
