// ============================================================
// FaceFit AI — Shared Administrator Layout
// ------------------------------------------------------------
// Renders the sidebar + topbar for every authenticated admin page
// from ONE place, so navigation is never copy-pasted again.
//
// This module is purely presentational:
//  - it does NOT guard pages (admin-guard.js still does that),
//  - it does NOT write to Firestore (the sidebar badges are
//    read-only count queries),
//  - it keeps the element ids the page scripts already rely on:
//    #admin-avatar, #admin-name, #admin-email, #logout-btn.
//
// Page scripts import this file FIRST so the shell exists in the
// DOM before they look up those ids.
// ============================================================

import { auth, db } from "./firebase-config.js";
import { onAuthStateChanged } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js";
import {
  collection,
  getCountFromServer,
  query,
  where
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

// ---------- Shared helpers (exported for page scripts) ----------

export function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value ?? "";
  return div.innerHTML;
}

export function formatDate(timestamp) {
  if (!timestamp || typeof timestamp.toDate !== "function") return "—";
  return timestamp.toDate().toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric"
  });
}

/** "Visionary Eyewear" -> "VE", "Lens & Co." -> "LC", "Juan Dela Cruz" -> "JD" */
export function initialsOf(text) {
  const words = String(text || "")
    .replace(/([a-z])([A-Z])/g, "$1 $2") // "ClearView" -> "Clear View"
    .split(/\s+/)
    .filter((w) => /[A-Za-z0-9]/.test(w));
  if (words.length === 0) return "?";
  const first = words[0][0];
  const second = words.length > 1 ? words[1][0] : "";
  return (first + second).toUpperCase();
}

/** Status pill. Stored values stay lowercase; only the label is capitalised. */
export function statusBadge(status, labels = {}) {
  const key = String(status || "pending").toLowerCase();
  const known = ["pending", "approved", "rejected", "hidden"];
  const cls = known.includes(key) ? `badge-status-${key}` : "badge-status-neutral";
  const defaultLabel = key.charAt(0).toUpperCase() + key.slice(1);
  const label = labels[key] || defaultLabel;
  return `<span class="badge-status ${cls}">${escapeHtml(label)}</span>`;
}

const FRAME_TINTS = [
  { lens: "#159aa8", bg: "#e1f3f5" },
  { lens: "#3b7dd8", bg: "#e5ecfa" },
  { lens: "#e4a03a", bg: "#fbf0dc" },
  { lens: "#c9628a", bg: "#fbe6ee" },
  { lens: "#5d6e7c", bg: "#e8ecef" }
];

/** Round frame thumbnail: the real frameImage when present, else a glasses glyph. */
export function frameIconHtml(frame) {
  const seed = String(frame?.id || frame?.model || "x");
  let sum = 0;
  for (const ch of seed) sum += ch.charCodeAt(0);
  const tint = FRAME_TINTS[sum % FRAME_TINTS.length];

  if (frame?.frameImage) {
    return `<span class="frame-ico" style="background:${tint.bg}"><img src="${escapeHtml(frame.frameImage)}" alt=""></span>`;
  }
  return `<span class="frame-ico" style="background:${tint.bg}">
    <svg width="36" height="36" viewBox="0 0 36 36" aria-hidden="true">
      <circle cx="10.5" cy="18" r="6.5" fill="${tint.lens}"/>
      <circle cx="25.5" cy="18" r="6.5" fill="${tint.lens}"/>
      <rect x="15" y="17" width="6" height="2" rx="1" fill="${tint.lens}"/>
    </svg></span>`;
}

/** Subscribe to the topbar search box. Callback receives the lower-cased term. */
export function onSearch(callback) {
  document.addEventListener("ff-search", (event) => callback(event.detail.term));
}

// ---------- Navigation definition ----------
// Routes/filenames are unchanged from earlier phases; only labels changed.

const NAV = [
  {
    label: "OVERVIEW",
    items: [{ key: "dashboard", text: "Dashboard", href: "dashboard.html", icon: "bi-grid" }]
  },
  {
    label: "MODERATION",
    items: [
      { key: "shops", text: "Shop Applications", href: "retailers.html", icon: "bi-shop", badge: "badge-shops" },
      { key: "frames", text: "Frame Review", href: "frame-listings.html", icon: "bi-infinity", badge: "badge-frames" }
    ]
  },
  {
    label: "RECORDS",
    items: [
      { key: "catalog", text: "Frame Catalog", href: "frame-catalog.html", icon: "bi-list" }
    ]
  }
];

function renderSidebar(activeKey) {
  const sections = NAV.map((section) => {
    const items = section.items
      .map((item) => {
        const active = item.key === activeKey ? " active" : "";
        const badge = item.badge
          ? `<span class="nav-badge d-none" id="${item.badge}">0</span>`
          : "";
        return `<a href="${item.href}" class="nav-item${active}"${active ? ' aria-current="page"' : ""}>
          <i class="bi ${item.icon}"></i><span>${item.text}</span>${badge}
        </a>`;
      })
      .join("");
    return `<div class="nav-label">${section.label}</div><nav>${items}</nav>`;
  }).join("");

  return `
    <aside class="sidebar" id="sidebar" aria-label="Administrator navigation">
      <a href="dashboard.html" class="brand">
        <img src="../assets/images/logo.png" alt="FaceFit AI logo">
        <span><span class="brand-name d-block">FaceFit AI</span><span class="brand-sub">Admin</span></span>
      </a>
      ${sections}
      <div class="sidebar-spacer"></div>
      <div class="sidebar-footer">
        <div class="avatar-circle" id="admin-avatar">AD</div>
        <div class="sidebar-account">
          <div class="account-name" id="admin-name">Administrator</div>
          <div class="account-email" id="admin-email">—</div>
        </div>
        <button class="logout-btn" id="logout-btn" title="Log out" aria-label="Log out">
          <i class="bi bi-box-arrow-right"></i>
        </button>
      </div>
    </aside>`;
}

function renderTopbar(title, subtitle, searchEnabled) {
  const search = searchEnabled
    ? `<label class="search-box">
         <i class="bi bi-search"></i>
         <input type="search" id="global-search" placeholder="Search this page…" aria-label="Search this page" autocomplete="off">
       </label>`
    : "";
  return `
    <header class="topbar">
      <button class="menu-btn" id="menu-btn" aria-label="Open navigation" aria-controls="sidebar" aria-expanded="false">
        <i class="bi bi-list"></i>
      </button>
      <div class="topbar-title">
        <h1>${escapeHtml(title)}</h1>
        <p>${escapeHtml(subtitle)}</p>
      </div>
      ${search}
      <button class="btn-icon bell-btn" disabled title="Notifications aren't available yet" aria-label="Notifications (not yet available)">
        <i class="bi bi-bell"></i>
      </button>
    </header>`;
}

// ---------- Build the shell ----------

function buildShell() {
  const host = document.getElementById("app-shell");
  if (!host) return;

  const content = host.querySelector("#page-content");
  const title = host.dataset.title || "";
  const subtitle = host.dataset.subtitle || "";
  const searchEnabled = host.dataset.search !== "off";

  host.classList.add("app-shell");
  host.innerHTML = `
    <div class="sidebar-backdrop" id="sidebar-backdrop"></div>
    ${renderSidebar(host.dataset.active)}
    <div class="app-main">
      ${renderTopbar(title, subtitle, searchEnabled)}
    </div>`;

  // Re-attach the page's own content (untouched) under the topbar.
  const main = document.createElement("main");
  main.className = "page-content";
  if (content) {
    while (content.firstChild) main.appendChild(content.firstChild);
  }
  host.querySelector(".app-main").appendChild(main);

  wireDrawer(host);
  wireSearch();
  preserveEmulatorLinks(host);
}

function preserveEmulatorLinks(host) {
  if (new URLSearchParams(window.location.search).get("useEmulators") !== "true") return;
  host.querySelectorAll('a[href]').forEach((link) => {
    const url = new URL(link.getAttribute("href"), window.location.href);
    if (url.origin !== window.location.origin) return;
    url.searchParams.set("useEmulators", "true");
    link.href = url.href;
  });
}

function wireDrawer(host) {
  const menuBtn = document.getElementById("menu-btn");
  const backdrop = document.getElementById("sidebar-backdrop");

  const setOpen = (open) => {
    host.classList.toggle("sidebar-open", open);
    menuBtn.setAttribute("aria-expanded", String(open));
  };

  menuBtn.addEventListener("click", () => setOpen(!host.classList.contains("sidebar-open")));
  backdrop.addEventListener("click", () => setOpen(false));
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") setOpen(false);
  });
  window.addEventListener("resize", () => {
    if (window.innerWidth >= 992) setOpen(false);
  });
}

function wireSearch() {
  const input = document.getElementById("global-search");
  if (!input) return;
  input.addEventListener("input", () => {
    document.dispatchEvent(
      new CustomEvent("ff-search", { detail: { term: input.value.trim().toLowerCase() } })
    );
  });
}

// ---------- Sidebar pending counts (read-only) ----------

async function countPending(collectionName, badgeId) {
  const badge = document.getElementById(badgeId);
  if (!badge) return;
  try {
    const snap = await getCountFromServer(
      query(collection(db, collectionName), where("status", "==", "pending"))
    );
    const n = snap.data().count;
    badge.textContent = n;
    badge.classList.toggle("d-none", n === 0);
  } catch (error) {
    // Badges are decorative; never let them break a page.
    console.warn(`[FF] Could not load pending count for ${collectionName}:`, error?.code || error);
    badge.classList.add("d-none");
  }
}

function loadBadges() {
  onAuthStateChanged(auth, (user) => {
    if (!user) return;
    countPending("retailers", "badge-shops");
    countPending("frames", "badge-frames");
  });
}

buildShell();
loadBadges();
