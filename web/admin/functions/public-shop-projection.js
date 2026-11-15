"use strict";

// Public shop data only. Retailer permits, owners and application contacts stay private.
function projectPublicShop(retailer) {
  if (!retailer || retailer.status !== "approved") return null;
  const text = value => typeof value === "string" ? value.trim().slice(0, 500) : "";
  const name = text(retailer.retailName);
  if (!name) return null;
  const shop = {name, address: text(retailer.retailAddress), status: "approved"};
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
