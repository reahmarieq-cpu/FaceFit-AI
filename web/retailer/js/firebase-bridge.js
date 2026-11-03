
import { auth, db } from "../../shared/firebase.js";
import { resolveRole, waitForUser } from "../../shared/roles.js";

import {
  createUserWithEmailAndPassword,
  signOut,
  sendPasswordResetEmail
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js";

import {
  doc,
  setDoc,
  updateDoc,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

window.FaceFitFirebase = {
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

    const credential = await createUserWithEmailAndPassword(
      auth,
      email,
      details.password
    );

    const uid = credential.user.uid;

    try {
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

        // Temporary pilot mode: no Firebase Storage upload
        permit: null,
        permitVerificationStatus: "pending_document",

        subscriptionPlan: "unselected",
        subscriptionStatus: "inactive",
        status: "pending",
        createdAt: serverTimestamp()
      });

      return { ok: true };
    } catch (error) {
      throw new Error(
        `Account was created, but the application could not be saved: ${error.message}. Contact the administrator before registering again.`
      );
    }
  }
};

window.dispatchEvent(new Event("facefit-firebase-ready"));
