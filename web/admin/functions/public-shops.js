"use strict";
const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {publishCurrentShop} = require("./public-shop-sync");

exports.syncPublicShop = onDocumentWritten({
  document: "retailers/{retailerId}",
  region: "asia-southeast1",
  retry: true
}, event =>
  publishCurrentShop(getFirestore(), event.params.retailerId, () => FieldValue.serverTimestamp()));
