import { escapeHtml, formatDate, initialsOf, onSearch } from "./admin-layout.js";
import { guardAdminPage } from "./admin-guard.js";
import { logoutAdmin } from "./auth.js";
import { db } from "./firebase-config.js";
import { collection, getDocs } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
const $ = (id) => document.getElementById(id);
let pending = [], term = "";
function renderQueue() {
  const matches = pending.filter(r => [r.retailName,r.contactPerson,r.retailAddress].some(v => String(v || "").toLowerCase().includes(term)));
  $("pending-list").innerHTML = matches.slice(0,5).map(r => `<div class="queue-row"><div class="entity"><span class="avatar-soft">${escapeHtml(initialsOf(r.retailName))}</span><div><a class="entity-title" href="retailer-detail.html?id=${encodeURIComponent(r.id)}">${escapeHtml(r.retailName || "Unnamed retailer")}</a><div class="entity-sub">${escapeHtml(r.retailAddress || "No address")} · ${formatDate(r.createdAt)}</div></div></div><a class="btn-soft btn-soft-teal" href="retailer-detail.html?id=${encodeURIComponent(r.id)}">Review</a></div>`).join("");
  $("pending-empty").classList.toggle("d-none", matches.length > 0);
}
onSearch(value => { term = value; renderQueue(); });
async function load() {
  try {
    const snap = await getDocs(collection(db,"retailers"));
    const all = snap.docs.map(d => ({id:d.id,...d.data()}));
    pending = all.filter(r => r.status === "pending").sort((a,b) => (b.createdAt?.seconds || 0)-(a.createdAt?.seconds || 0));
    const approved = all.filter(r => r.status === "approved");
    $("stat-pending-apps").textContent = pending.length;
    $("stat-approved-shops").textContent = approved.length;
    $("stat-active-subs").textContent = approved.filter(r => String(r.subscriptionStatus || "").toLowerCase() === "active").length;
    $("stat-inactive-subs").textContent = approved.filter(r => ["inactive","expired"].includes(String(r.subscriptionStatus || "").toLowerCase())).length;
    renderQueue(); $("metrics-content").classList.remove("d-none");
  } catch(e) { console.error(e); $("metrics-error").textContent = "Unable to load retailer records. Check Firestore access and try again."; $("metrics-error").classList.remove("d-none"); }
  finally { $("metrics-loading").classList.add("d-none"); }
}
guardAdminPage().then(({user}) => { $("admin-email").textContent=user.email || "—"; $("admin-name").textContent="Administrator"; $("admin-avatar").textContent=(user.email || "AD").slice(0,2).toUpperCase(); load(); });
$("logout-btn").addEventListener("click",async()=>{ try {await logoutAdmin();}catch(e){console.error(e);} });
