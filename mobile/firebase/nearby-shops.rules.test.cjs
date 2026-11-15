const fs=require('node:fs');
const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,where,updateDoc}=require('firebase/firestore');
let env;
before(async()=>{
  if(!process.env.FACEFIT_RULES_FILE)throw new Error('Set FACEFIT_RULES_FILE to the complete candidate rules file');
  env=await initializeTestEnvironment({projectId:'demo-facefit-nearby',firestore:{host:'127.0.0.1',port:8184,
    rules:fs.readFileSync(process.env.FACEFIT_RULES_FILE,'utf8')}});
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context=>{
    const db=context.firestore();
    const seed={
      'admins/admin':{role:'admin'},
      'publicShops/shop-a':{name:'A',status:'approved'},
      'publicShops/shop-b':{name:'B',status:'approved'},
      'publicShops/blocked':{name:'Blocked',status:'pending'},
      'retailers/shop-a':{userId:'shop-a',status:'approved',permit:'private'},
      'retailers/blocked':{userId:'blocked',status:'approved'},
      'retailers/shop-a/frames/live':{retailerId:'shop-a',id:'live',status:'live',name:'Live'},
      'retailers/shop-a/frames/approved':{retailerId:'shop-a',id:'approved',status:'approved',name:'Approved'},
      'retailers/shop-a/frames/pending':{retailerId:'shop-a',id:'pending',status:'pending'},
      'retailers/shop-a/frames/draft':{retailerId:'shop-a',id:'draft',status:'draft'},
      'retailers/blocked/frames/live':{retailerId:'blocked',id:'live',status:'live'},
      'frames/catalog':{retailerId:'shop-a',status:'approved'},
      'frames/legacy':{retailId:'shop-a',status:'approved'},
      'frames/both':{retailerId:'shop-a',retailId:'shop-a',status:'approved'},
      'frames/other':{retailerId:'shop-b',status:'approved'},
      'frames/pending':{retailerId:'shop-a',status:'pending'},
      'frames/blocked':{retailerId:'blocked',status:'approved'}
    };
    for(const [target,data] of Object.entries(seed))await setDoc(doc(db,target),data);
  });
});
after(async()=>{if(env)await env.cleanup();});
const customer=()=>env.authenticatedContext('customer').firestore();

test('customer query returns only published retailer products',async()=>{
  const db=customer();
  const result=await assertSucceeds(getDocs(query(collection(db,'retailers/shop-a/frames'),where('status','in',['live','approved']))));
  assert.deepEqual(result.docs.map(d=>d.id).sort(),['approved','live']);
});
test('both top-level owner schemas support the exact mobile queries',async()=>{
  const db=customer();
  for(const field of ['retailerId','retailId']){
    const result=await assertSucceeds(getDocs(query(collection(db,'frames'),where(field,'==','shop-a'),where('status','==','approved'))));
    assert.equal(result.size,2);
  }
});
test('customer cannot read drafts, pending products or products of unpublished shops',async()=>{
  for(const target of ['retailers/shop-a/frames/pending','retailers/shop-a/frames/draft',
    'retailers/blocked/frames/live','frames/pending','frames/blocked']){
    await assertFails(getDoc(doc(customer(),target)));
  }
});
test('unfiltered product queries are denied',async()=>{
  await assertFails(getDocs(collection(customer(),'retailers/shop-a/frames')));
  await assertFails(getDocs(collection(customer(),'frames')));
});
test('signed-out users cannot read public products',async()=>{
  const db=env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db,'retailers/shop-a/frames/live')));
  await assertFails(getDoc(doc(db,'frames/catalog')));
});
test('customers cannot edit products or read private retailer applications',async()=>{
  await assertFails(updateDoc(doc(customer(),'retailers/shop-a/frames/live'),{name:'Changed'}));
  await assertFails(setDoc(doc(customer(),'frames/new'),{retailerId:'shop-a',status:'approved'}));
  await assertFails(getDoc(doc(customer(),'retailers/shop-a')));
});
test('owner read/edit access and protected moderation fields remain intact',async()=>{
  const owner=env.authenticatedContext('shop-a').firestore();
  await assertSucceeds(getDoc(doc(owner,'retailers/shop-a/frames/draft')));
  await assertSucceeds(updateDoc(doc(owner,'retailers/shop-a/frames/live'),{name:'Owner edit'}));
  await assertFails(updateDoc(doc(owner,'retailers/shop-a/frames/live'),{status:'approved'}));
});
test('existing admin access remains intact',async()=>{
  const db=env.authenticatedContext('admin').firestore();
  await assertSucceeds(getDoc(doc(db,'frames/pending')));
  await assertSucceeds(getDoc(doc(db,'retailers/shop-a/frames/draft')));
});

test('product detail document reads are allowed for published frames',async()=>{
  for(const target of ['retailers/shop-a/frames/live','retailers/shop-a/frames/approved','frames/catalog','frames/legacy']){
    await assertSucceeds(getDoc(doc(customer(),target)));
  }
});
