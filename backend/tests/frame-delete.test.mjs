import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import { test } from "node:test";
import express from "express";
import { frameAsset } from "../firebase-files.js";

const source = readFileSync(new URL("../server.js", import.meta.url), "utf8");
const authenticateSource = source.slice(source.indexOf("async function authenticate("), source.indexOf('app.get("/auth/check"'));
const removeSource = source.slice(source.indexOf("async function removeFrameAsset("), source.indexOf("async function authenticate("));
const deleteSource = source.slice(source.indexOf('app.delete("/frames/:frameId"'), source.indexOf("// Clean up replaced assets"));

async function exercise(settings = {}) {
  const uid = "retailer-a";
  const frameId = "frame-test";
  const paths = [];
  const removals = [];
  const updateTime = { seconds: 123 };
  let deleted = false;
  const frame = {
    retailerId: uid,
    productImagePath: `firebase://frame-images/${uid}/${frameId}/photo.png`,
    vtoAssetPath: `firebase://vto-models/${uid}/${frameId}/model.glb`,
    ...settings.frame,
  };
  const app = express();
  vm.runInNewContext(`${removeSource}\n${authenticateSource}\n${deleteSource}`, {
    app,
    frameAsset,
    firebaseFiles: () => ({ file: path => ({ delete: async options => {
      assert.equal(options.ignoreNotFound, true);
      const [category, ...parts] = path.split("/");
      removals.push([category, [parts.join("/")]]);
      if (settings.failBucket === category) throw new Error("Storage unavailable");
    } }) }),
    console: { error() {} },
    getAuth: () => ({ verifyIdToken: async token => {
      if (token !== "test-token") throw new Error("Invalid token");
      return { uid };
    } }),
    getFirestore: () => ({ doc: path => {
      paths.push(path);
      assert.ok(path === `retailers/${uid}` || path === `retailers/${uid}/frames/${frameId}`);
      return {
        get: async () => path === `retailers/${uid}`
          ? { exists: settings.retailerExists !== false, data: () => ({ status: settings.status || "approved" }) }
          : { exists: settings.exists !== false, data: () => frame, updateTime },
        delete: async precondition => {
          assert.equal(precondition.lastUpdateTime, updateTime);
          if (settings.changed) throw Object.assign(new Error("Changed"), { code: 9 });
          deleted = true;
        },
      };
    } }),
  });
  const server = app.listen(0, "127.0.0.1");
  await new Promise(resolve => server.once("listening", resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/frames/${settings.id || frameId}`, {
      method: "DELETE",
      headers: settings.noToken ? {} : { Authorization: `Bearer ${settings.token || "test-token"}` },
    });
    return { status: response.status, body: await response.json(), paths, removals, deleted };
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
}

test("owner deletion removes the photo and GLB before the Firestore record", async () => {
  const result = await exercise();
  assert.equal(result.status, 200);
  assert.equal(result.deleted, true);
  assert.deepEqual(result.removals, [
    ["frame-images", ["retailer-a/frame-test/photo.png"]],
    ["vto-models", ["retailer-a/frame-test/model.glb"]],
  ]);
});

for (const [name, settings, status] of [
  ["missing authentication", { noToken: true }, 401],
  ["invalid authentication", { token: "bad" }, 401],
  ["unapproved retailer", { status: "pending" }, 403],
  ["missing retailer", { retailerExists: false }, 403],
  ["another retailer's frame ID", { exists: false }, 404],
  ["mismatched record owner", { frame: { retailerId: "retailer-b" } }, 403],
  ["another retailer's photo", { frame: { productImagePath: "firebase://frame-images/retailer-b/frame-test/photo.png" } }, 409],
  ["another retailer's GLB", { frame: { vtoAssetPath: "firebase://vto-models/retailer-b/frame-test/model.glb" } }, 409],
  ["path traversal", { frame: { vtoAssetPath: "firebase://vto-models/retailer-a/frame-test/../../retailer-b/model.glb" } }, 409],
  ["invalid frame ID", { id: "bad.id" }, 400],
  ["legacy storage path", { frame: { vtoAssetPath: "vto-models/retailer-a/frame-test/model.glb" } }, 409],
]) {
  test(`${name} cannot delete any data`, async () => {
    const result = await exercise(settings);
    assert.equal(result.status, status);
    assert.equal(result.deleted, false);
    assert.deepEqual(result.removals, []);
  });
}

for (const bucket of ["frame-images", "vto-models"]) {
  test(`${bucket} failure retains the record for retry`, async () => {
    const result = await exercise({ failBucket: bucket });
    assert.equal(result.status, 500);
    assert.equal(result.deleted, false);
    assert.equal((await exercise()).status, 200);
  });
}

test("concurrent edit retains the changed record", async () => {
  const result = await exercise({ changed: true });
  assert.equal(result.status, 409);
  assert.equal(result.deleted, false);
});

test("photo-only frame can be deleted", async () => {
  const result = await exercise({ frame: { vtoAssetPath: null } });
  assert.equal(result.status, 200);
  assert.equal(result.removals.length, 1);
});


test("frame without storage assets can be deleted", async () => {
  const result = await exercise({ frame: { productImagePath: null, vtoAssetPath: null } });
  assert.equal(result.status, 200);
  assert.equal(result.deleted, true);
  assert.deepEqual(result.removals, []);
});
