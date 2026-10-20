/* ==========================================================================
   main.js
   Entry point: seeds demo data, then starts the script for the current page
   (each HTML file declares its page name in <body data-page="...">).
   ========================================================================== */

document.addEventListener("DOMContentLoaded", () => {
  FaceFitStorage.ensureSeedData();

  const pageName = document.body.dataset.page;
  const shopPages = ["dashboard", "catalog", "add-frame", "profile"];

  if (pageName === "login") {
    FaceFitAuth.initLoginPage();
  } else if (pageName === "register") {
    FaceFitAuth.initRegisterPage();
  } else if (pageName === "pending") {
    FaceFitAuth.initPendingPage();
  } else if (shopPages.includes(pageName)) {
    FaceFitShop.initPage(pageName);
  }
});
