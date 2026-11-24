"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {publishCurrentShop} = require("./public-shop-sync");
const {main} = require("./backfill-public-shops");

function fixture(data, subscription = null) {
  const actions = [];
  const transaction = {
    get: async source => {
      if (source === "subscriptions/shop-a") return {exists:subscription !== null,data:() => subscription};
      assert.equal(source,"retailers/shop-a"); return {exists:data !== null,data:() => data};
    },
    set: (target,value) => actions.push(["set",target,value]),
    delete: target => actions.push(["delete",target])
  };
  return {actions,db:{collection:name => ({doc:id => `${name}/${id}`}),runTransaction:fn => fn(transaction)}};
}
test("sync uses the latest retailer and same ID, replacing stale public data", async () => {
  const {db,actions} = fixture({status:"approved",retailName:"Current",shopLocation:{lat:10,lng:123},permit:"private"});
  assert.equal(await publishCurrentShop(db,"shop-a",() => "server-time"),true);
  assert.deepEqual(actions,[["set","publicShops/shop-a",{name:"Current",address:"",status:"approved",subscribed:false,latitude:10,longitude:123,updatedAt:"server-time"}]]);
});
test("sync reads the shop's subscription and publishes only the yes/no flag", async () => {
  const {db,actions} = fixture({status:"approved",retailName:"Current"},{status:"demo_active",planType:"bundle"});
  assert.equal(await publishCurrentShop(db,"shop-a",() => "server-time"),true);
  assert.equal(actions[0][2].subscribed,true);
  assert.equal(actions[0][2].planType,undefined);
});
test("delayed sync cannot resurrect a deleted or unapproved retailer", async () => {
  for (const value of [null,{status:"rejected",retailName:"A"},{status:"pending",retailName:"A"}]) {
    const {db,actions} = fixture(value);
    assert.equal(await publishCurrentShop(db,"shop-a",() => "server-time"),false);
    assert.deepEqual(actions,[["delete","publicShops/shop-a"]]);
  }
});
test("backfill rejects absent/wrong project and unknown flags before loading credentials", async () => {
  await assert.rejects(main([]),/Specify --project/);
  await assert.rejects(main(["--project","facefit-ai-ca57f"]),/Specify --project/);
  await assert.rejects(main(["--project","facefit-ai-capstone","--unknown"]),/Use only/);
});
