"use strict";

// Public shop data only. Retailer permits, owners and application contacts stay private.
// From the subscription only "subscribed" (yes/no) and its end date "subscribedUntil" are
// published; the app ranks shops by them. Subscribed means status "demo_active" and not expired.
// subscribedUntil lets the app stop treating a shop as subscribed once that date passes,
// even though no document changes at that moment.
function projectPublicShop(retailer, subscription = null, nowMs = Date.now()) {
  if (!retailer || retailer.status !== "approved") return null;
  const text = value => typeof value === "string" ? value.trim().slice(0, 500) : "";
  const name = text(retailer.retailName);
  if (!name) return null;
  const shop = {name, address: text(retailer.retailAddress), status: "approved", subscribed: false};
  if (subscription?.status === "demo_active") {
    const expiresAt = subscription.expiresAt;
    if (expiresAt == null) shop.subscribed = true; // No end date recorded.
    else {
      const expiresMs = timestampMillis(expiresAt);
      // An unreadable end date never counts as subscribed.
      if (expiresMs !== null && expiresMs > nowMs) {
        shop.subscribed = true;
        shop.subscribedUntil = expiresAt;
      }
    }
  }
  const logoUrl = [retailer.logoUrl, retailer.logo, retailer.profileImageUrl, retailer.profileImage]
    .map(publicLogoUrl).find(Boolean);
  if (logoUrl) shop.logoUrl = logoUrl;
  // Keep a pair from one source. Never combine a current pin with old coordinates.
  const currentPin = retailer.shopLocation;
  const legacyPin = retailer.latitude != null || retailer.longitude != null
    ? {latitude: retailer.latitude, longitude: retailer.longitude} : retailer.location;
  const latitude = currentPin != null ? currentPin.lat : legacyPin?.latitude;
  const longitude = currentPin != null ? currentPin.lng : legacyPin?.longitude;
  if (typeof latitude === "number" && Number.isFinite(latitude) && Math.abs(latitude) <= 90 &&
      typeof longitude === "number" && Number.isFinite(longitude) && Math.abs(longitude) <= 180) {
    shop.latitude = latitude;
    shop.longitude = longitude;
  }
  if (typeof retailer.rating === "number" && Number.isFinite(retailer.rating) &&
      retailer.rating >= 0 && retailer.rating <= 5 &&
      Number.isInteger(retailer.ratingCount) && retailer.ratingCount > 0) shop.rating = retailer.rating;
  return shop;
}

// Firestore Timestamp (or Date) to milliseconds; anything else is unreadable.
function timestampMillis(value) {
  const ms = typeof value?.toMillis === "function" ? value.toMillis()
    : value instanceof Date ? value.getTime() : NaN;
  return Number.isFinite(ms) ? ms : null;
}

// Accept published business image URLs, never local paths or embedded data.
function publicLogoUrl(value) {
  if (typeof value !== "string") return null;
  const text = value.trim();
  if (!text || text.length > 4096 || /[\u0000-\u0020]/u.test(text)) return null;
  try {
    const url = new URL(text);
    return url.protocol === "https:" && url.hostname && !url.username && !url.password ? url.href : null;
  } catch { return null; }
}

module.exports = {projectPublicShop};
