import "dotenv/config";
import express from "express";
import cors from "cors";
import multer from "multer";
import { initializeApp, cert } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { config } from "dotenv";
import { firebaseFiles, saveFirebaseFile, frameAsset } from "./firebase-files.js";
import { createMayaSandbox } from "./maya-sandbox.js";

const app = express();
const port = Number(process.env.PORT || 3000);
const keyFile = new URL("./serviceAccountKey.json", import.meta.url);
if (existsSync(keyFile)) {
  initializeApp({credential:cert(JSON.parse(readFileSync(keyFile,"utf8"))), projectId:process.env.FIREBASE_PROJECT_ID});
} else {
  initializeApp({projectId:process.env.FIREBASE_PROJECT_ID});
  console.warn("Firebase serviceAccountKey.json missing. Token verification may work, but role checks require server credentials.");
}
app.use(cors({origin:(origin,callback)=>callback(null,!origin || /^http:\/\/(localhost|127\.0\.0\.1):(8000|5500)$/.test(origin))}));
app.use(express.json());
app.get("/health",(_req,res)=>res.json({status:"ok",service:"FaceFit AI Upload API",storageConfigured:true,storageProvider:"firebase"}));

async function removeFrameAsset(path) {
  await firebaseFiles().file(path).delete({ignoreNotFound:true});
}
async function authenticate(req,res,next) {
  try {
    const match = /^Bearer (.+)$/.exec(req.get("authorization") || "");
    if (!match) return res.status(401).json({error:"Missing Firebase ID token"});
    req.identity = await getAuth().verifyIdToken(match[1]);
    next();
  } catch(e) { return res.status(401).json({error:"Invalid or expired Firebase ID token"}); }
}
app.get("/auth/check",authenticate,(req,res)=>res.json({authenticated:true,uid:req.identity.uid}));
config({path:fileURLToPath(new URL("./.env.maya-sandbox", import.meta.url)),quiet:true});
const maya = createMayaSandbox({
  db:getFirestore(), publicKey:process.env.MAYA_SANDBOX_PUBLIC_KEY, secretKey:process.env.MAYA_SANDBOX_SECRET_KEY,
  timestamp:()=>FieldValue.serverTimestamp(),
});
app.post("/subscriptions/maya-sandbox/:action",authenticate,async(req,res)=>{
  try {
    const uid=req.identity.uid;
    if (req.params.action==="start") return res.json(await maya.start(uid,req.body?.planType,req.get("origin")));
    if (req.params.action==="confirm") return res.json(await maya.confirm(uid,req.body?.reference));
    if (req.params.action==="cancel-plan") return res.json(await maya.cancelPlan(uid));
    return res.status(404).json({error:"Unknown checkout action"});
  } catch(error) {
    if (!error.status) console.error("Maya sandbox checkout failed",error.name,error.code);
    return res.status(error.status||502).json({error:error.status?error.message:"Maya sandbox is unavailable. Try again."});
  }
});
const options = {
  permit:{bucket:"business-permits",max:5*1024*1024,mimes:{"application/pdf":"pdf","image/jpeg":"jpg","image/png":"png"}},
  profile:{bucket:"shop-logos",max:2*1024*1024,mimes:{"image/jpeg":"jpg","image/png":"png","image/webp":"webp"}},
  product:{bucket:"frame-images",max:2*1024*1024,mimes:{"image/jpeg":"jpg","image/png":"png","image/webp":"webp"}},
  vto:{bucket:"vto-models",max:10*1024*1024,mimes:{"model/gltf-binary":"glb","application/octet-stream":"glb"}}
};
const upload = multer({storage:multer.memoryStorage(),limits:{fileSize:10*1024*1024,files:1}});
app.post("/uploads/:kind",authenticate,upload.single("file"),async(req,res)=>{
  try {
    const kind=req.params.kind, rule=options[kind];
    if (!rule) return res.status(400).json({error:"Unknown upload type"});
    if (!req.file) return res.status(400).json({error:"Please choose a file"});
    const ext=rule.mimes[req.file.mimetype];
    if (!ext || req.file.size>rule.max || req.file.size===0) return res.status(400).json({error:`Invalid ${kind} file (maximum ${rule.max/1048576} MB)`});
    if (kind==="vto" && !req.file.originalname.toLowerCase().endsWith(".glb")) return res.status(400).json({error:"VTO file must be GLB"});
    const uid=req.identity.uid;
    const retailer=await getFirestore().doc(`retailers/${uid}`).get();
    if (kind!=="permit" && (!retailer.exists || retailer.data()?.status!=="approved")) return res.status(403).json({error:"Approved retailer account required"});
    if (kind==="vto") {
      const sub=await getFirestore().doc(`subscriptions/${uid}`).get();
      const data=sub.data() || {};
      if (!(data.planType==="bundle" && data.status==="demo_active" && data.paymentMode==="simulation")) return res.status(403).json({error:"Active Retailer + Kiosk subscription required"});
    }
    const frameId=String(req.body.frameId||"").replace(/[^a-zA-Z0-9_-]/g,"").slice(0,100);
    if (["product","vto"].includes(kind) && !frameId) return res.status(400).json({error:"Frame ID required"});
    const name=`${crypto.randomUUID()}.${ext}`;
    const path=frameId?`${uid}/${frameId}/${name}`:`${uid}/${name}`;
    const objectPath=`${rule.bucket}/${path}`;
    const url=await saveFirebaseFile(objectPath,req.file.buffer,ext==="glb"?"model/gltf-binary":req.file.mimetype,kind==="profile" || kind==="product");
    res.json({path:`firebase://${objectPath}`,url,type:ext});
  } catch(e) {console.error("Upload error",e);res.status(500).json({error:e.message||"Upload failed"});}
});
// Delete a retailer-owned frame and its associated storage assets.
// The document is retained if storage cleanup fails, so deletion can be retried.
app.delete("/frames/:frameId", authenticate, async (req,res) => {
  try {
    const uid=req.identity.uid;
    const frameId=req.params.frameId;
    if (!/^[a-zA-Z0-9_-]{1,100}$/.test(frameId)) return res.status(400).json({error:"Invalid frame ID"});
    const retailer=await getFirestore().doc(`retailers/${uid}`).get();
    if (!retailer.exists || retailer.data()?.status!=="approved") return res.status(403).json({error:"Approved retailer required"});
    const ref=getFirestore().doc(`retailers/${uid}/frames/${frameId}`);
    const snapshot=await ref.get();
    if (!snapshot.exists) return res.status(404).json({error:"Frame not found"});
    const frame=snapshot.data();
    if (frame.retailerId!==uid) return res.status(403).json({error:"Not your frame"});
    const files=[['frame-images',frame.productImagePath],['vto-models',frame.vtoAssetPath]];
    const assets=[];
    for (const [bucket,stored] of files) {
      if (!stored) continue;
      const asset=frameAsset(stored,bucket,uid,frameId);
      if (!asset) return res.status(409).json({error:`Unrecognized ${bucket} asset path; no files deleted`});
      assets.push(asset);
    }
    for (const asset of assets) await removeFrameAsset(asset);
    await ref.delete({lastUpdateTime:snapshot.updateTime});
    return res.json({ok:true,deletedFrameId:frameId});
  } catch(e) {
    console.error("Frame deletion failed",e);
    if (e.code===9) return res.status(409).json({error:"Frame changed during deletion. Refresh and retry."});
    return res.status(500).json({error:e.message||"Deletion failed; retry"});
  }
});
// Clean up replaced assets only after the frame record points to the new file.
app.post("/frames/:frameId/cleanup-asset",authenticate,async(req,res)=>{
  try {
    const uid=req.identity.uid, frameId=req.params.frameId;
    if(!/^[a-zA-Z0-9_-]{1,100}$/.test(frameId)) return res.status(400).json({error:"Invalid frame ID"});
    const bucket=req.body?.kind==="product"?"frame-images":req.body?.kind==="vto"?"vto-models":null;
    const stored=req.body?.path;
    const asset=bucket && frameAsset(stored,bucket,uid,frameId);
    if(!asset) return res.status(400).json({error:"Invalid asset path"});
    const snap=await getFirestore().doc(`retailers/${uid}/frames/${frameId}`).get();
    if(!snap.exists || snap.data()?.retailerId!==uid) return res.status(403).json({error:"Frame ownership not verified"});
    const current=req.body.kind==="product"?snap.data().productImagePath:snap.data().vtoAssetPath;
    if(current===stored) return res.status(409).json({error:"Cannot remove active asset"});
    await removeFrameAsset(asset);
    res.json({ok:true});
  }catch(e){console.error("Asset cleanup failed",e);res.status(500).json({error:e.message||"Cleanup failed"});}
});
app.get("/private-file",authenticate,async(req,res)=>{
  try {
    const reference=String(req.query.ref||"");
    const match=/^firebase:\/\/(business-permits|vto-models)\/([a-zA-Z0-9_-]+(?:\/[a-zA-Z0-9_-]+)?\/[a-zA-Z0-9_-]+\.(?:pdf|jpg|jpeg|png|glb))$/.exec(reference);
    if(!match) return res.status(400).json({error:"Invalid private file reference"});
    const [,bucket,path]=match;
    const owner=path.split("/")[0];
    const uid=req.identity.uid;
    const admin=await getFirestore().doc(`admins/${uid}`).get();
    const isAdmin=admin.exists && admin.data()?.role==="admin";
    if(bucket==="business-permits" && !isAdmin && owner!==uid) return res.status(403).json({error:"Not authorized"});
    if(bucket==="vto-models" && !isAdmin && owner!==uid) return res.status(403).json({error:"Not authorized"});
    const [url]=await firebaseFiles().file(`${bucket}/${path}`).getSignedUrl({version:"v4",action:"read",expires:Date.now()+300000});
    res.json({url});
  } catch(e){res.status(500).json({error:e.message||"Unable to open file"});}
});
app.use((err,_req,res,next)=>{if(err instanceof multer.MulterError) return res.status(400).json({error:err.code==="LIMIT_FILE_SIZE"?"File exceeds 10 MB":err.message});next(err);});
app.listen(port,()=>console.log(`FaceFit AI API running at http://localhost:${port}`));
