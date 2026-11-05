import "dotenv/config";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { initializeApp, cert } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { firebaseFiles, storageBucket } from "../firebase-files.js";

if (!process.argv.includes("--disposable-approved")) throw new Error("Explicit approval is required for this live disposable-data test.");
initializeApp({ credential: cert(JSON.parse(readFileSync(new URL("../serviceAccountKey.json", import.meta.url), "utf8"))) });
const auth = getAuth();
const db = getFirestore();
const storage = firebaseFiles();
const config = readFileSync(new URL("../../web/shared/firebase.js", import.meta.url), "utf8");
const apiKey = /apiKey:\s*"([^"]+)"/.exec(config)[1];
const uid = `disposable-delete-${Date.now()}`;
const otherUid = `${uid}-other`;
const frameId = "cleanup-test";
const retailer = db.doc(`retailers/${uid}`);
const otherRetailer = db.doc(`retailers/${otherUid}`);
const frameRef = retailer.collection("frames").doc(frameId);
const otherFrame = otherRetailer.collection("frames").doc("other-frame");
const subscription = db.doc(`subscriptions/${uid}`);
const uploaded = [];
let userCreated = false;
try {
  await auth.createUser({ uid, displayName: "Disposable cleanup verification" });
  userCreated = true;
  await retailer.create({ status: "approved", disposableTest: true });
  await otherRetailer.create({ status: "approved", disposableTest: true });
  await otherFrame.create({ retailerId: otherUid, name: "Disposable isolation sentinel" });
  await subscription.create({ planType: "bundle", status: "demo_active", paymentMode: "simulation", disposableTest: true });
  const customToken = await auth.createCustomToken(uid);
  const signIn = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${apiKey}`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token: customToken, returnSecureToken: true }),
  });
  const session = await signIn.json();
  assert.ok(signIn.ok, session.error?.message || "Test authentication failed");
  const headers = { Authorization: `Bearer ${session.idToken}` };
  const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=", "base64");
  const scene = Buffer.from(JSON.stringify({ asset: { version: "2.0" }, scene: 0, scenes: [{ nodes: [] }] }).padEnd(80, " "));
  const glb = Buffer.alloc(20 + scene.length);
  glb.writeUInt32LE(0x46546c67, 0);
  glb.writeUInt32LE(2, 4);
  glb.writeUInt32LE(glb.length, 8);
  glb.writeUInt32LE(scene.length, 12);
  glb.writeUInt32LE(0x4e4f534a, 16);
  scene.copy(glb, 20);
  const record = { retailerId: uid, name: "Disposable cleanup verification", disposableTest: true };
  for (const [kind, bytes, filename, type, field] of [
    ["product", png, "test.png", "image/png", "productImagePath"],
    ["vto", glb, "test.glb", "model/gltf-binary", "vtoAssetPath"],
    ["permit", png, "test.png", "image/png", null],
    ["profile", png, "test.png", "image/png", null],
  ]) {
    const form = new FormData();
    form.append("file", new Blob([bytes], { type }), filename);
    if (field) form.append("frameId", frameId);
    const response = await fetch(`http://localhost:3000/uploads/${kind}`, { method: "POST", headers, body: form });
    const result = await response.json();
    assert.equal(response.status, 200, result.error);
    assert.ok(result.path.startsWith("firebase://"));
    uploaded.push(result.path.slice("firebase://".length));
    if (kind === "product" || kind === "profile") assert.equal((await fetch(result.url)).status, 200);
    if (kind === "vto" || kind === "permit") {
      const privateResponse = await fetch(`http://localhost:3000/private-file?ref=${encodeURIComponent(result.url)}`, { headers });
      assert.equal(privateResponse.status, 200);
      assert.equal((await fetch((await privateResponse.json()).url)).status, 200);
      const bareUrl = `https://firebasestorage.googleapis.com/v0/b/${storageBucket}/o/${encodeURIComponent(uploaded.at(-1))}?alt=media`;
      assert.equal((await fetch(bareUrl)).ok, false);
      assert.equal((await fetch(`https://storage.googleapis.com/${storageBucket}/${uploaded.at(-1)}`)).ok, false);
    }
    if (field) record[field] = result.path;
  }
  await frameRef.create(record);
  for (const stored of uploaded) {
    assert.equal((await storage.file(stored).exists())[0], true);
  }
  console.log("PASS: all four Firebase upload types work; permits and GLBs require authorized access.");
  const forbidden = await fetch("http://localhost:3000/frames/other-frame", { method: "DELETE", headers });
  assert.equal(forbidden.status, 404);
  assert.equal((await otherFrame.get()).data().name, "Disposable isolation sentinel");
  console.log("PASS: another retailer's test frame is unchanged after a cross-retailer delete attempt.");
  const response = await fetch(`http://localhost:3000/frames/${frameId}`, { method: "DELETE", headers });
  const result = await response.json();
  assert.equal(response.status, 200, result.error);
  assert.equal((await frameRef.get()).exists, false);
  for (const stored of uploaded) {
    assert.equal((await storage.file(stored).exists())[0], !/^(frame-images|vto-models)\//.test(stored));
  }
  assert.equal((await otherFrame.get()).exists, true);
  console.log("PASS: live DELETE removed the Firestore record and both Firebase objects; the other retailer's test record remains.");
  const repeated = await fetch(`http://localhost:3000/frames/${frameId}`, { method: "DELETE", headers });
  assert.equal(repeated.status, 404);
  console.log("PASS: repeated deletion returns Frame not found.");
} finally {
  for (const stored of uploaded) {
    assert.ok(stored.split("/")[1] === uid);
    await storage.file(stored).delete({ignoreNotFound:true});
  }
  await frameRef.delete();
  await otherFrame.delete();
  await subscription.delete();
  await retailer.delete();
  await otherRetailer.delete();
  if (userCreated) await auth.deleteUser(uid);
  console.log("CLEANUP: disposable test accounts and records removed.");
}
