// Run with: node tests/auth.test.cjs
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const local = new Map();
const session = new Map();
let application;
let redirect;
let failWrites = false;
const storage = map => ({
  getItem: key => map.get(key) ?? null,
  setItem: (key, value) => {
    if (failWrites) throw new Error("Storage full");
    map.set(key, String(value));
  },
  removeItem: key => map.delete(key),
});
const context = vm.createContext({
  localStorage: storage(local),
  sessionStorage: storage(session),
  FaceFitRegistration: { init: register => { application = register; } },
  window: { location: { replace: url => { redirect = url; } } },
});
for (const script of ["storage", "auth", "registration", "main"]) {
  const source = fs.readFileSync(path.join(__dirname, "../js", `${script}.js`), "utf8");
  new vm.Script(source); // Parse every changed script, execute the auth dependencies.
  if (["storage", "auth"].includes(script)) vm.runInContext(source, context);
}
const run = code => vm.runInContext(code, context);
run("FaceFitStorage.ensureSeedData(); FaceFitAuth.initRegisterPage(); FaceFitStorage.setSessionEmail(FaceFitStorage.DEMO_ACCOUNT_EMAIL)");
const details = {
  email: " TEST@example.com ", password: "test-only-password", shopName: " Test Optical ",
  ownerFirstName: " Test ", ownerLastName: " Owner ", phone: "09171234567", businessPermit: "CITY-123",
  address: "123 Test Street, Cebu City",
  addressParts: { unit: "", street: "123 Test Street", barangay: "Test Barangay", city: "Cebu City", province: "Cebu", postalCode: "6000", country: "Philippines" },
  permit: { name: "permit.pdf", type: "application/pdf", size: 4, dataUrl: "data:application/pdf;base64,dGVzdA==" },
};
assert.equal(application(details).ok, true);
const saved = run('FaceFitStorage.findAccountByEmail("test@example.com")');
assert.equal(saved.shop.name, "Test Optical");
assert.equal(saved.shop.ownerFirstName, "Test");
assert.equal(saved.shop.ownerLastName, "Owner");
assert.equal(saved.shop.contactPerson, "Test Owner");
assert.equal(saved.shop.status, "pending");
assert.equal(saved.shop.address, details.address);
assert.equal(saved.shop.addressParts.city, "Cebu City");
assert.equal(saved.shop.addressParts.postalCode, "6000");
assert.equal(Object.hasOwn(saved.shop, "location"), false);
assert.equal(saved.shop.businessPermit.dataUrl, details.permit.dataUrl);
assert.ok(saved.shop.submittedAt);
assert.equal(run("FaceFitStorage.getSessionEmail()"), null);
assert.equal(application(details).ok, false, "Duplicate email must be rejected");
run('FaceFitStorage.setSessionEmail("test@example.com")');
assert.equal(run("FaceFitAuth.requireApprovedSession()"), null);
assert.equal(redirect, "pending.html");
assert.equal(run("FaceFitStorage.getSessionEmail()"), null);
assert.equal(run("FaceFitStorage.getPendingShopName()"), "Test Optical");
run('FaceFitStorage.setSessionEmail(FaceFitStorage.DEMO_ACCOUNT_EMAIL)');
assert.equal(run("FaceFitAuth.requireApprovedSession().shop.status"), "approved");
run("FaceFitStorage.clearSession()");
assert.equal(run("FaceFitAuth.requireApprovedSession()"), null);
assert.equal(redirect, "index.html");
failWrites = true;
assert.equal(application({ ...details, email: "another@example.com" }).ok, false);
assert.equal(run('FaceFitStorage.findAccountByEmail("another@example.com")'), null);
console.log("PASS: script syntax, application persistence, duplicate email, pending/approved/missing-session guards, and storage failure.");
