import { escapeHtml, initialsOf, onSearch, formatDate } from "./admin-layout.js";
import { guardAdminPage } from "./admin-guard.js";
import { logoutAdmin } from "./auth.js";
import { db } from "./firebase-config.js";
import { collection, getDocs } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
const $ = id => document.getElementById(id);
let records = [], search = "";
function render() {
 const matches = records.filter(r => [r.retailName,r.contactPerson,r.emailAddress,r.subscriptionPlan,r.subscriptionStatus].some(v=>String(v||"").toLowerCase().includes(search)));
 $("retailer-list-body").innerHTML = matches.map(r=>`<tr><td><div class="entity"><span class="avatar-soft">${escapeHtml(initialsOf(r.retailName))}</span><div><a class="entity-title" href="retailer-profile.html?id=${encodeURIComponent(r.id)}">${escapeHtml(r.retailName || "Unnamed retailer")}</a><div class="entity-sub">${escapeHtml(r.retailAddress || "—")}</div></div></div></td><td>${escapeHtml(r.contactPerson || "—")}</td><td>${escapeHtml(r.subscriptionPlan || "Not set")}</td><td>${escapeHtml(r.subscriptionStatus || "Not set")}</td><td>${formatDate(r.subscriptionExpiry || r.subscriptionExpiresAt || r.subscriptionEndDate)}</td><td class="td-end"><a class="btn-soft btn-soft-teal" href="retailer-profile.html?id=${encodeURIComponent(r.id)}">View</a></td></tr>`).join("");
 $("empty-state").classList.toggle("d-none",matches.length>0);
}
onSearch(t=>{search=t;render();});
guardAdminPage().then(async ({user})=>{
 $("admin-email").textContent=user.email || "—"; $("admin-name").textContent="Administrator"; $("admin-avatar").textContent=(user.email||"AD").slice(0,2).toUpperCase();
 try {const snap=await getDocs(collection(db,"retailers"));records=snap.docs.map(d=>({id:d.id,...d.data()})).filter(r=>r.status==="approved");render();}catch(e){console.error(e);$("error-state").textContent="Could not load approved retailers. Check Firestore access.";$("error-state").classList.remove("d-none");}finally{$("loading-state").classList.add("d-none");}
});
$("logout-btn").addEventListener("click",async()=>{try{await logoutAdmin();}catch(e){console.error(e);}});
