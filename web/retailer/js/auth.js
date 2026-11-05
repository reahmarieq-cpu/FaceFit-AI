const FaceFitAuth = (()=>{
  const LOGIN="../admin/admin/login.html";
  function signOut(){ window.FaceFitFirebase.logout().finally(()=>window.location.assign(LOGIN)); }
  async function requireApprovedSession(){
    try {
      const role=await window.FaceFitFirebase.currentRole();
      if(role.role!=="retailer") {window.location.replace(LOGIN);return null;}
      if(role.data.status!=="approved") {window.location.replace("pending.html");return null;}
      const d=role.data;
      return {email:d.emailAddress||"",shop:{name:d.retailName||"Shop",ownerFirstName:d.ownerFirstName||"",ownerLastName:d.ownerLastName||"",contactPerson:d.contactPerson||"",phone:d.contactNumber||"",businessPermitNumber:d.businessPermitNumber||"",address:d.retailAddress||"",addressParts:d.addressParts||{},status:d.status,profileImageUrl:d.profileImageUrl||"",profileViews:0}};
    } catch(error){console.error("Retailer session check failed",error);document.body.textContent="Unable to verify your account. Check your connection and Firebase rules, then reload.";return null;}
  }
  function initLoginPage(){window.location.replace(LOGIN);}
  function initRegisterPage(){FaceFitRegistration.init(async details=>{try{return await window.FaceFitFirebase.register(details);}catch(e){return {ok:false,message:e.message};}});}
  async function initPendingPage(){
    const role=await window.FaceFitFirebase.currentRole();
    if(role.role==="admin") {window.location.replace("../admin/admin/dashboard.html");return;}
    if(role.role!=="retailer") {window.location.replace(LOGIN);return;}
    if(role.data.status==="approved") {window.location.replace("dashboard.html");return;}
    const shop=document.getElementById("submittedShopName");if(shop)shop.textContent=role.data.retailName||"your shop";
    const title=document.querySelector(".status-card__title");const desc=document.querySelector(".status-card__text");
    if(role.data.status==="rejected") {if(title)title.textContent="Application rejected";if(desc)desc.textContent="Your retailer application was not approved. Please contact FaceFit AI support for details.";}
    const chip=document.querySelector(".status-chip");if(chip)chip.textContent=`Status: ${role.data.status||"pending"}`;
  }
  return {signOut,requireApprovedSession,initLoginPage,initRegisterPage,initPendingPage};
})();
