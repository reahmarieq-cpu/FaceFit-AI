function startFaceFitRetailer(){
 const page=document.body.dataset.page;
 if(page==="login") FaceFitAuth.initLoginPage();
 else if(page==="register") FaceFitAuth.initRegisterPage();
 else if(page==="pending") FaceFitAuth.initPendingPage();
 else if(["dashboard","catalog","add-frame","profile"].includes(page)) FaceFitShop.initPage(page);
}
if(window.FaceFitFirebase) document.addEventListener("DOMContentLoaded",startFaceFitRetailer);
else window.addEventListener("facefit-firebase-ready",()=>{if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",startFaceFitRetailer,{once:true});else startFaceFitRetailer();},{once:true});
