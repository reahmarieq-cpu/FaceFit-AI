import { auth, db } from "./firebase.js";
import { doc, getDoc, collection, query, where, getDocs, limit } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
import { onAuthStateChanged } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js";
export function waitForUser() { return new Promise(resolve => { const stop=onAuthStateChanged(auth, user=>{stop();resolve(user);},()=>{stop();resolve(null);}); }); }
export async function resolveRole(user) {
  if (!user) return { role:"guest" };
  const a=await getDoc(doc(db,"admins",user.uid));
  if(a.exists() && a.data().role==="admin") return {role:"admin"};
  // New applications use the UID as the document ID; legacy records may use other IDs.
  const own=await getDoc(doc(db,"retailers",user.uid));
  if(own.exists() && own.data().userId===user.uid) return {role:"retailer", id:own.id, data:own.data()};
  const matches=await getDocs(query(collection(db,"retailers"),where("userId","==",user.uid),limit(1)));
  if(!matches.empty) return {role:"retailer",id:matches.docs[0].id,data:matches.docs[0].data()};
  return {role:"unknown"};
}
export function roleUrl(role) {
  if(role.role==="admin") return new URL("../admin/admin/dashboard.html", import.meta.url).href;
  if(role.role==="retailer") return new URL(role.data.status==="approved"?"../retailer/dashboard.html":"../retailer/pending.html", import.meta.url).href;
  return null;
}
