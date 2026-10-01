// ============================================================
// FaceFit AI — Administrator Cloud Functions
// ------------------------------------------------------------
// Trusted, server-side retailer approval/rejection.
//
// Why this exists instead of a client-side Firestore write:
// the browser can be tampered with, but this code runs on
// Google's servers using the Admin SDK, which bypasses
// firestore.rules entirely. The admin-role check below is
// therefore the REAL enforcement point for this action.
// ============================================================

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();
const db = getFirestore();

/**
 * Throws if the caller is not signed in, or is not a verified
 * Administrator per admins/{uid}. Mirrors the exact same check
 * used client-side in admin-guard.js — this is the trusted
 * server-side version of it.
 */
async function assertCallerIsAdmin(auth) {
  if (!auth) {
    throw new HttpsError("unauthenticated", "You must be signed in to perform this action.");
  }

  const adminSnap = await db.collection("admins").doc(auth.uid).get();
  if (!adminSnap.exists || adminSnap.data().role !== "admin") {
    throw new HttpsError("permission-denied", "This account is not authorized as an Administrator.");
  }
}

/**
 * Shared logic for approve/reject: validates input, loads the
 * retailer, and atomically updates its status + writes the
 * matching notification in a single batch.
 */
async function decideRetailerApplication({ retailerId, decision, auth }) {
  await assertCallerIsAdmin(auth);

  if (!retailerId || typeof retailerId !== "string") {
    throw new HttpsError("invalid-argument", "A valid retailerId is required.");
  }

  const retailerRef = db.collection("retailers").doc(retailerId);
  const retailerSnap = await retailerRef.get();

  if (!retailerSnap.exists) {
    throw new HttpsError("not-found", `No retailer application found with ID "${retailerId}".`);
  }

  const retailer = retailerSnap.data();

  const status = decision === "approve" ? "approved" : "rejected";
  const notificationType =
    decision === "approve" ? "retailer_application_approved" : "retailer_application_rejected";
  const message =
    decision === "approve"
      ? `Your retailer application for "${retailer.retailName}" has been approved.`
      : `Your retailer application for "${retailer.retailName}" has been rejected.`;

  const batch = db.batch();

  batch.update(retailerRef, { status });

  const notificationRef = db.collection("notifications").doc();
  batch.set(notificationRef, {
    userId: retailer.userId,
    type: notificationType,
    message,
    isRead: false,
    createdAt: FieldValue.serverTimestamp()
  });

  await batch.commit();

  return { success: true, retailerId, status };
}

exports.approveRetailer = onCall(async (request) => {
  const { retailerId } = request.data || {};
  return decideRetailerApplication({
    retailerId,
    decision: "approve",
    auth: request.auth
  });
});

exports.rejectRetailer = onCall(async (request) => {
  const { retailerId } = request.data || {};
  return decideRetailerApplication({
    retailerId,
    decision: "reject",
    auth: request.auth
  });
});

/**
 * Shared logic for validate/hide: validates input, loads the frame,
 * looks up the owning retailer (frames.retailId -> retailers.userId)
 * to know who the notification belongs to, then atomically updates
 * the frame's status and writes the notification in one batch.
 *
 * Frame Catalog reuses this same function for its "Hide" action —
 * there is no separate catalog collection, per the single FRAME
 * entity in the ERD.
 */
async function decideFrameListing({ frameId, decision, auth }) {
  await assertCallerIsAdmin(auth);

  if (!frameId || typeof frameId !== "string") {
    throw new HttpsError("invalid-argument", "A valid frameId is required.");
  }

  const frameRef = db.collection("frames").doc(frameId);
  const frameSnap = await frameRef.get();

  if (!frameSnap.exists) {
    throw new HttpsError("not-found", `No frame listing found with ID "${frameId}".`);
  }

  const frame = frameSnap.data();

  // Look up the owning retailer so the notification goes to the
  // correct userId — frames don't store userId directly, only
  // retailId, per the approved FRAME schema.
  let ownerUserId = null;
  if (frame.retailId) {
    const retailerSnap = await db.collection("retailers").doc(frame.retailId).get();
    if (retailerSnap.exists) {
      ownerUserId = retailerSnap.data().userId || null;
    }
  }

  const status = decision === "validate" ? "approved" : "hidden";
  const notificationType =
    decision === "validate" ? "frame_listing_approved" : "frame_listing_hidden";
  const frameName = [frame.brand, frame.model].filter(Boolean).join(" ") || "Your frame listing";
  const message =
    decision === "validate"
      ? `${frameName} has been approved and is now visible in the catalog.`
      : `${frameName} has been hidden and is no longer visible.`;

  const batch = db.batch();

  batch.update(frameRef, { status });

  // Only create a notification if we actually resolved an owner —
  // never write a notification with a missing/undefined userId.
  if (ownerUserId) {
    const notificationRef = db.collection("notifications").doc();
    batch.set(notificationRef, {
      userId: ownerUserId,
      type: notificationType,
      message,
      isRead: false,
      createdAt: FieldValue.serverTimestamp()
    });
  }

  await batch.commit();

  return { success: true, frameId, status };
}

exports.validateFrame = onCall(async (request) => {
  const { frameId } = request.data || {};
  return decideFrameListing({
    frameId,
    decision: "validate",
    auth: request.auth
  });
});

exports.hideFrame = onCall(async (request) => {
  const { frameId } = request.data || {};
  return decideFrameListing({
    frameId,
    decision: "hide",
    auth: request.auth
  });
});
