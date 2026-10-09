// FaceFit AI — shared approval backend selection.
// Change ONLY this setting after deploying Cloud Functions on Blaze.
// Do not automatically fall back to client writes on function errors.
export const APPROVAL_MODE = "firestore"; // "firestore" or "functions"

import { db, functions } from "./firebase-config.js";
import { doc, runTransaction } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";
import { httpsCallable } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-functions.js";

const actions = {
  approveRetailer: { collection: "retailers", idKey: "retailerId", status: "approved" },
  rejectRetailer: { collection: "retailers", idKey: "retailerId", status: "rejected" },
  validateFrame: { collection: "frames", idKey: "frameId", status: "approved" },
  hideFrame: { collection: "frames", idKey: "frameId", status: "hidden" }
};

export async function executeApproval(action, id) {
  const spec = actions[action];
  if (!spec) throw new Error("Unknown review action.");
  if (typeof id !== "string" || !id.trim()) throw new Error("Missing record ID.");

  if (APPROVAL_MODE === "functions") {
    const result = await httpsCallable(functions, action)({ [spec.idKey]: id });
    return result?.data?.status || spec.status;
  }
  if (APPROVAL_MODE !== "firestore") throw new Error("Invalid approval mode configuration.");

  // Firestore rules must independently verify admin role and allow ONLY
  // a pending -> approved/rejected/hidden status-only update.
  await runTransaction(db, async (transaction) => {
    const ref = doc(db, spec.collection, id);
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists()) throw new Error("Record no longer exists.");
    if (snapshot.data().status !== "pending") {
      throw new Error("This application has already been reviewed. Refresh the page.");
    }
    transaction.update(ref, { status: spec.status });
  });
  return spec.status;
}
