
import { auth, db } from "../../shared/firebase.js";
import { resolveRole, waitForUser } from "../../shared/roles.js";

import {
  createUserWithEmailAndPassword,
  signOut,
  sendPasswordResetEmail
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js";

import {
  collection, doc, getDocs,
  setDoc,
  updateDoc,
  serverTimestamp, getDoc
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

const API = "http://localhost:3000";
async function uploadToStorage(file, kind, frameId = "") {
  const user = await waitForUser();
  if (!user) throw new Error("Sign in before uploading files.");
  const form = new FormData();
  form.append("file", file);
  if (frameId) form.append("frameId", frameId);
  let response;
  try {
    response = await fetch(`${API}/uploads/${kind}`, {
      method:"POST", headers:{Authorization:`Bearer ${await user.getIdToken()}`}, body:form
    });
  } catch (_) { throw new Error("Cannot reach upload API. Start the backend with npm start on port 3000."); }
  const data = await response.json().catch(()=>({}));
  if (!response.ok) throw new Error(data.error || `Upload failed (${response.status})`);
  return data;
}
async function uploadRetailerImage(file, kind) {
  if (!file) throw new Error("Choose an image or business permit.");
  return (await uploadToStorage(file, kind)).url;
}

window.FaceFitFirebase = {
  uploadRetailerImage,
  async kioskEntitlement() {
    const user = await waitForUser();
    if (!user) throw new Error("Sign in to check subscription.");
    const role = await resolveRole(user);
    const snap = await getDoc(doc(db, "subscriptions", role?.id || user.uid));
    const data = snap.exists() ? snap.data() : null;
    return Boolean(data?.planType === "bundle" && data?.status === "demo_active" && data?.paymentMode === "simulation");
  },
  async uploadFrameAsset(file, frameId, kind) {
    const user = await waitForUser();
    if (!user) throw new Error("Sign in before uploading frame files.");
    if (!file) throw new Error("Choose a file.");
    const isVto = kind === "vto";
    const ext = file.name.toLowerCase().split(".").pop();
    if (!(isVto ? ["glb"].includes(ext) : ["png","jpg","jpeg","webp"].includes(ext))) throw new Error("Unsupported file format.");
    if (file.size > (isVto ? 10 : 2) * 1024 * 1024) throw new Error("File exceeds the upload size limit.");
    if (isVto && !(await this.kioskEntitlement())) throw new Error("Active Retailer + Kiosk subscription required.");
    return uploadToStorage(file, isVto ? "vto" : "product", String(frameId).replace(/[^a-zA-Z0-9_-]/g, ""));
  },
  async deleteFrameRecord(frameId) {
    const user=await waitForUser();
    if(!user) throw new Error("Sign in to delete frames.");
    const response=await fetch(`${API}/frames/${encodeURIComponent(frameId)}`,{
      method:"DELETE",headers:{Authorization:`Bearer ${await user.getIdToken()}`}
    });
    const result=await response.json().catch(()=>({}));
    if(!response.ok) throw new Error(result.error||(response.status===404 ? "Delete endpoint unavailable. Restart the FaceFit backend on port 3000, then retry." : `Deletion failed (${response.status})`));
    return result;
  },
  async cleanupReplacedAsset(frameId,kind,path) {
    if(!path) return;
    const user=await waitForUser();
    const response=await fetch(`${API}/frames/${encodeURIComponent(frameId)}/cleanup-asset`,{
      method:"POST",headers:{Authorization:`Bearer ${await user.getIdToken()}`,"Content-Type":"application/json"},
      body:JSON.stringify({kind,path})
    });
    if(!response.ok) console.warn("Old asset cleanup needs retry:",await response.text());
  },
  async saveFrameRecord(frame) {
    const user = await waitForUser();
    if (!user) throw new Error("Sign in before saving frames.");
    const safeId = String(frame.id).replace(/[^a-zA-Z0-9_-]/g, "");
    const record = {...frame, retailerId:user.uid, imageDataUrl:null, updatedAt:serverTimestamp()};
    await setDoc(doc(db, "retailers", user.uid, "frames", safeId), record, {merge:true});
  },
  async loadFrameRecords() {
    const user = await waitForUser();
    if (!user) throw new Error("Sign in to load frames.");
    const snapshot = await getDocs(collection(db, "retailers", user.uid, "frames"));
    return snapshot.docs.map(entry => {
      const record = entry.data();
      return { ...record, id: entry.id, createdAt: record.createdAt?.toMillis?.() ?? record.createdAt };
    });
  },
  async currentRole() {
    return resolveRole(await waitForUser());
  },

  async logout() {
    await signOut(auth);
  },

  async updateRetailerProfile(fields) {
    const user = await waitForUser();
    if (!user) throw new Error("Not signed in");
    await updateDoc(doc(db, "retailers", user.uid), fields);
  },

  async reset(email) {
    await sendPasswordResetEmail(auth, email);
  },

  async register(details) {
    const email = details.email.trim().toLowerCase();

    // If a previous attempt created Auth but failed during upload, resume with
    // the same account instead of creating a duplicate.
    let credential;
    try {
      credential = await createUserWithEmailAndPassword(auth, email, details.password);
    } catch (error) {
      if (error.code !== "auth/email-already-in-use") throw error;
      const { signInWithEmailAndPassword } = await import("https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js");
      credential = await signInWithEmailAndPassword(auth, email, details.password);
      const existing = await getDoc(doc(db, "retailers", credential.user.uid));
      if (existing.exists()) throw new Error("This retailer account is already registered. Please log in instead.");
    }

    const uid = credential.user.uid;

    try {
      const permitUrl = await uploadRetailerImage(details.permitFile, "permit");
      await setDoc(doc(db, "retailers", uid), {
        userId: uid,
        retailName: details.shopName.trim(),
        contactPerson:
          `${details.ownerFirstName.trim()} ${details.ownerLastName.trim()}`,
        ownerFirstName: details.ownerFirstName.trim(),
        ownerLastName: details.ownerLastName.trim(),
        emailAddress: email,
        contactNumber: details.phone,
        retailAddress: details.address,
        addressParts: details.addressParts,
        businessPermitNumber: details.businessPermit,

        permit: permitUrl,

        subscriptionPlan: "unselected",
        subscriptionStatus: "inactive",
        status: "pending",
        createdAt: serverTimestamp()
      });

      return { ok: true };
    } catch (error) {
      throw new Error(
        `Your account exists, but the application was not saved: ${error.message}. You can retry registration using the same email and password after fixing the issue.`
      );
    }
  }
};

window.dispatchEvent(new Event("facefit-firebase-ready"));
