"use strict";
// One-time publishing of existing retailers. Dry-run by default; ADC required.
// Never put credentials or downloaded service-account keys in the project.
async function main(args) {
  const allowedProject = "facefit-ai-capstone";
  const projectIndex = args.indexOf("--project");
  const projectId = projectIndex >= 0 ? args[projectIndex + 1] : undefined;
  if (projectId !== allowedProject) throw new Error(`Specify --project ${allowedProject}; no data was changed.`);
  const unknown = args.filter((value, index) => value !== "--apply" && value !== "--project" && index !== projectIndex + 1);
  if (unknown.length) throw new Error("Use only --project facefit-ai-capstone and optional --apply.");
  const {initializeApp, applicationDefault} = require("firebase-admin/app");
  const {getFirestore, FieldValue, FieldPath} = require("firebase-admin/firestore");
  const {projectPublicShop} = require("./public-shop-projection");
  const {publishCurrentShop} = require("./public-shop-sync");
  initializeApp({projectId, credential: applicationDefault()});
  const db = getFirestore();
  const apply = args.includes("--apply");
  let cursor = null;
  let scanned = 0;
  let published = 0;
  while (true) {
    let query = db.collection("retailers").orderBy(FieldPath.documentId()).limit(200);
    if (cursor) query = query.startAfter(cursor);
    const page = await query.get();
    if (page.empty) break;
    for (const document of page.docs) {
      const visible = apply
        ? await publishCurrentShop(db, document.id, () => FieldValue.serverTimestamp())
        : projectPublicShop(document.data()) !== null;
      if (visible) published++;
      scanned++;
    }
    cursor = page.docs[page.docs.length - 1];
  }
  console.log(`${apply ? "Applied" : "Dry run (no writes)"}: ${scanned} retailers checked; ${published} approved, named shops ${apply ? "published" : "eligible"} in ${projectId}.`);
}

if (require.main === module) main(process.argv.slice(2)).catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
module.exports = {main};
