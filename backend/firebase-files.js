import { randomUUID } from "node:crypto";
import { getStorage, getDownloadURL } from "firebase-admin/storage";

export const storageBucket = "facefit-ai-capstone.firebasestorage.app";
export const firebaseFiles = () => getStorage().bucket(storageBucket);

export async function saveFirebaseFile(path, bytes, contentType, shareable) {
  const file = firebaseFiles().file(path);
  await file.save(bytes, {
    resumable: false,
    preconditionOpts: { ifGenerationMatch: 0 },
    metadata: { contentType, ...(shareable ? { metadata: { firebaseStorageDownloadTokens: randomUUID() } } : {}) },
  });
  return shareable ? getDownloadURL(file) : `firebase://${path}`;
}

export function frameAsset(stored, category, uid, frameId) {
  if (typeof stored !== "string" || !stored.startsWith("firebase://")) return null;
  const path = stored.slice("firebase://".length);
  const prefix = `${category}/${uid}/${frameId}/`;
  const extension = category === "vto-models" ? "glb" : "jpg|jpeg|png|webp";
  if (!path.startsWith(prefix) || !new RegExp(`^[a-zA-Z0-9_-]+\\.(${extension})$`).test(path.slice(prefix.length))) return null;
  return path;
}
