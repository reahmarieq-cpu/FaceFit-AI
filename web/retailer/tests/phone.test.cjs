// Run with: node tests/phone.test.cjs
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const context = vm.createContext({});
for (const script of ["vendor/libphonenumber-max", "registration"]) {
  vm.runInContext(fs.readFileSync(path.join(__dirname, "../js", `${script}.js`), "utf8"), context);
}
const normalize = vm.runInContext("FaceFitRegistration.normalizePhone", context);
const clean = vm.runInContext("FaceFitRegistration.cleanPhoneDigits", context);
assert.equal(clean("09123456789", "PH", "63"), "9123456789");
assert.equal(normalize("09123456789", "PH", "63"), "+639123456789");
assert.equal(normalize("9123456789", "PH", "63"), "+639123456789");
assert.equal(normalize("639123456789", "PH", "63"), "+639123456789");
assert.equal(normalize("63639123456789", "PH", "63"), null);
assert.equal(normalize("81234567", "SG", "65"), "+6581234567");
assert.equal(normalize("02079460958", "GB", "44"), "+442079460958");
assert.equal(normalize("2025550123", "US", "1"), "+12025550123");
assert.equal(normalize("4165550123", "CA", "1"), "+14165550123");
assert.equal(normalize("2025550123", "CA", "1"), null, "Shared calling codes still validate against the selected country");
assert.equal(normalize("9123456789", "SG", "65"), null);
for (const value of ["", "123", "99999999999999999", "912 3456789", "912-3456789", "912abc3456789", "+639123456789", "(912)3456789", "9123456789\n"]) {
  assert.equal(normalize(value, "PH", "63"), null, `Reject invalid national input: ${JSON.stringify(value)}`);
}
console.log("PASS: leading zeros, country-specific validation, shared codes, duplicate prefixes, and forbidden characters.");
