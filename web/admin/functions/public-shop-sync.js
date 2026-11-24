"use strict";
const {projectPublicShop} = require("./public-shop-projection");

async function publishCurrentShop(db, retailerId, serverTimestamp) {
  const source = db.collection("retailers").doc(retailerId);
  const subscriptionSource = db.collection("subscriptions").doc(retailerId);
  const target = db.collection("publicShops").doc(retailerId);
  // Read the latest source in a transaction: delayed events cannot restore a
  // deleted/rejected shop or overwrite its newer name, map location or subscription.
  return db.runTransaction(async transaction => {
    const current = await transaction.get(source);
    const subscription = await transaction.get(subscriptionSource);
    const shop = projectPublicShop(current.exists ? current.data() : null,
      subscription.exists ? subscription.data() : null);
    if (shop) transaction.set(target, {...shop, updatedAt: serverTimestamp()});
    else transaction.delete(target);
    return shop !== null;
  });
}

module.exports = {publishCurrentShop};
