import "./admin-layout.js";
import { guardAdminPage } from "./admin-guard.js";
import { logoutAdmin } from "./auth.js";
import { db } from "./firebase-config.js";
import { renderPermit } from "./permit-preview.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
const $ = id=>document.getElementById(id);
const set = (id,v)=>{$(id).textContent=v===undefined||v===null||v===""?"—":String(v)};
guardAdminPage().then(async ({user})=>{
 set("admin-name","Administrator");set("admin-email",user.email);set("admin-avatar",(user.email||"AD").slice(0,2).toUpperCase());
 try {const id=new URLSearchParams(location.search).get("id");if(!id)throw Error("Missing retailer ID");const snap=await getDoc(doc(db,"retailers",id));if(!snap.exists())throw Error("Retailer not found");const r=snap.data();if(r.status!=="approved")throw Error("This retailer is not approved");
for (const key of [
  "retailName",
  "contactPerson",
  "contactNumber",
  "emailAddress",
  "retailAddress"
]) {
  set(`field-${key}`, r[key]);
}

// Read the retailer's current subscription separately.
const subscriptionSnap = await getDoc(
  doc(db, "subscriptions", id)
);

if (subscriptionSnap.exists()) {
  const subscription = subscriptionSnap.data();

  const planName =
    subscription.planType === "bundle"
      ? "Retailer + Kiosk — ₱798/month"
      : subscription.planType === "retailer"
        ? "Retailer Plan — ₱499/month"
        : "Unselected";

  const statusName =
    subscription.status === "demo_active"
      ? "Active"
      : subscription.status === "demo_cancelled"
        ? "Cancelled"
        : "Inactive";

  set("field-subscriptionPlan", planName);
  set("field-subscriptionStatus", statusName);
} else {
  set("field-subscriptionPlan", "Unselected");
  set("field-subscriptionStatus", "Inactive");
}
set("field-createdAt",r.createdAt?.toDate?.().toLocaleString()||"—");renderPermit(r.permit,$("field-permit"));$("status-badge").textContent="Approved";$("status-badge").className="badge-status badge-status-approved";$("detail-content").classList.remove("d-none");}catch(e){$("error-state").textContent=e.message;$("error-state").classList.remove("d-none");}finally{$("loading-state").classList.add("d-none");}
});
$("logout-btn").addEventListener("click",async()=>{try{await logoutAdmin();}catch(e){console.error(e);}});
