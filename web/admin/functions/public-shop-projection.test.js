"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const {projectPublicShop} = require("./public-shop-projection");

test("confirmed portal fields map to the mobile directory without private fields", () => {
  const row = projectPublicShop({status:"approved",retailName:" Glasses ",retailAddress:" Cebu ",
    shopLocation:{lat:10.338,lng:123.910}, permit:"private",businessPermitNumber:"private",
    contactNumber:"private",emailAddress:"private",ownerFirstName:"private",userId:"private"});
  assert.deepEqual(row,{name:"Glasses",address:"Cebu",status:"approved",subscribed:false,latitude:10.338,longitude:123.910});
});
test("pending, rejected, deleted and unnamed retailers never publish", () => {
  for (const value of [null,{}, {status:"pending",retailName:"A"},
    {status:"rejected",retailName:"A"},{status:"approved",retailName:" "}]) assert.equal(projectPublicShop(value),null);
});
test("current location takes precedence over legacy locations", () => {
  const row = projectPublicShop({status:"approved",retailName:"A",shopLocation:{lat:10,lng:123},
    latitude:1,longitude:2,location:{latitude:3,longitude:4}});
  assert.equal(row.latitude,10); assert.equal(row.longitude,123);
});
test("malformed current pins never restore stale coordinates", () => {
  for (const pin of [{lat:10},{lng:123},{lat:"10",lng:123},{lat:NaN,lng:123},
    {lat:91,lng:123},{lat:10,lng:181},"invalid"]) {
    assert.deepEqual(projectPublicShop({status:"approved",retailName:"A",latitude:1,longitude:2,shopLocation:pin}),
      {name:"A",address:"",status:"approved",subscribed:false});
  }
});
test("only an active demo subscription publishes subscribed, and nothing else from it", () => {
  const shop = {status:"approved",retailName:"A"};
  assert.equal(projectPublicShop(shop).subscribed,false);
  assert.equal(projectPublicShop(shop,{status:"demo_active"}).subscribed,true);
  for (const status of ["demo_cancelled","expired","pending",undefined]) {
    assert.equal(projectPublicShop(shop,{status}).subscribed,false);
  }
  const row = projectPublicShop(shop,{status:"demo_active",planType:"bundle",paymentMode:"simulation",userId:"x"});
  assert.deepEqual(Object.keys(row).sort(),["address","name","status","subscribed"]);
});
test("expired or unreadable subscription end dates are not subscribed; active ones publish the end date", () => {
  const shop = {status:"approved",retailName:"A"};
  const now = Date.parse("2026-11-24T00:00:00Z");
  const at = iso => ({toMillis: () => Date.parse(iso)});
  const future = at("2026-12-10T00:00:00Z");
  const active = projectPublicShop(shop,{status:"demo_active",expiresAt:future,amount:798},now);
  assert.equal(active.subscribed,true);
  assert.equal(active.subscribedUntil,future);
  assert.deepEqual(Object.keys(active).sort(),["address","name","status","subscribed","subscribedUntil"]);
  const expired = projectPublicShop(shop,{status:"demo_active",expiresAt:at("2026-11-01T00:00:00Z")},now);
  assert.equal(expired.subscribed,false); assert.equal(expired.subscribedUntil,undefined);
  for (const expiresAt of ["2026-12-10","soon",{toMillis:() => NaN},12345]) {
    assert.equal(projectPublicShop(shop,{status:"demo_active",expiresAt},now).subscribed,false);
  }
  assert.equal(projectPublicShop(shop,{status:"demo_active",expiresAt:new Date("2026-12-10T00:00:00Z")},now).subscribed,true);
  assert.equal(projectPublicShop(shop,{status:"demo_cancelled",expiresAt:future},now).subscribedUntil,undefined);
});
test("legacy coordinate formats remain supported without mixing pairs", () => {
  for (const fields of [{latitude:10,longitude:123},{location:{latitude:10,longitude:123}}]) {
    assert.equal(projectPublicShop({status:"approved",retailName:"A",...fields}).longitude,123);
  }
  assert.equal(projectPublicShop({status:"approved",retailName:"A",latitude:10,location:{longitude:123}}).longitude,undefined);
});
test("ratings require a genuine nonempty aggregate", () => {
  assert.equal(projectPublicShop({status:"approved",retailName:"A",rating:5}).rating,undefined);
  assert.equal(projectPublicShop({status:"approved",retailName:"A",rating:4.5,ratingCount:2}).rating,4.5);
});

test("business logo aliases publish one public HTTPS image and respect explicit logo precedence", () => {
  for (const field of ["logoUrl","logo","profileImageUrl","profileImage"]) {
    assert.equal(projectPublicShop({status:"approved",retailName:"A",[field]:"https://example.com/logo.png"}).logoUrl,
      "https://example.com/logo.png");
  }
  assert.equal(projectPublicShop({status:"approved",retailName:"A",logoUrl:"https://example.com/logo.png",
    profileImageUrl:"https://example.com/profile.png"}).logoUrl,"https://example.com/logo.png");
});
test("missing, removed or unsafe logo values leave the icon fallback available", () => {
  for (const value of [undefined,"", "data:image/png;base64,a", "file:///a", "http://example.com/a",
    "https://user:pass@example.com/a", "https://example.com/bad path.png", {url:"https://example.com/a"}]) {
    assert.equal(projectPublicShop({status:"approved",retailName:"A",logoUrl:value}).logoUrl,undefined);
  }
  assert.equal(projectPublicShop({status:"approved",retailName:"A",logoUrl:"invalid",
    profileImageUrl:"https://example.com/profile.png"}).logoUrl,"https://example.com/profile.png");
});
