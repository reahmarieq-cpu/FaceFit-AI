/* ==========================================================================
   storage.js
   Thin wrapper around localStorage. Keeps accounts, the signed-in session and
   each shop's frame listings. A demo account is seeded on first load.

   NOTE: this is a front-end demo. Passwords are kept in localStorage in plain
   text and must be replaced by a real backend before any production use.
   ========================================================================== */

const FaceFitStorage = (() => {
  const STORAGE_KEYS = {
    accounts: "facefit.accounts",
    sessionEmail: "facefit.sessionEmail",
    framesPrefix: "facefit.frames.",
    pendingShopName: "facefit.pendingShopName",
    notificationsRead: "facefit.notificationsRead",
  };

  const DEMO_ACCOUNT_EMAIL = "personnel@clearview.ph";
  const DEMO_ACCOUNT_PASSWORD = "clearview";
  const ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;

  function readJson(key, fallbackValue) {
    try {
      const rawValue = localStorage.getItem(key);
      return rawValue ? JSON.parse(rawValue) : fallbackValue;
    } catch (error) {
      return fallbackValue;
    }
  }

  function writeJson(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (error) {
      return false; // storage full or unavailable
    }
  }

  /* Accounts ------------------------------------------------------------ */
  function getAccounts() {
    return readJson(STORAGE_KEYS.accounts, []);
  }

  function saveAccounts(accounts) {
    return writeJson(STORAGE_KEYS.accounts, accounts);
  }

  function findAccountByEmail(email) {
    const normalizedEmail = String(email).trim().toLowerCase();
    return getAccounts().find((account) => account.email === normalizedEmail) || null;
  }

  /* Session ------------------------------------------------------------- */
  function getSessionEmail() {
    try {
      return localStorage.getItem(STORAGE_KEYS.sessionEmail);
    } catch (error) {
      return null;
    }
  }

  function setSessionEmail(email) {
    try {
      localStorage.setItem(STORAGE_KEYS.sessionEmail, email);
    } catch (error) {
      /* ignore: session just will not persist */
    }
  }

  function clearSession() {
    try {
      localStorage.removeItem(STORAGE_KEYS.sessionEmail);
    } catch (error) {
      /* ignore */
    }
  }

  /* Frames -------------------------------------------------------------- */
  function loadFrames(email) {
    return readJson(STORAGE_KEYS.framesPrefix + email, []);
  }

  function saveFrames(email, frames) {
    return writeJson(STORAGE_KEYS.framesPrefix + email, frames);
  }

  /* Small flags --------------------------------------------------------- */
  function setPendingShopName(shopName) {
    try {
      sessionStorage.setItem(STORAGE_KEYS.pendingShopName, shopName);
    } catch (error) {
      /* ignore */
    }
  }

  function getPendingShopName() {
    try {
      return sessionStorage.getItem(STORAGE_KEYS.pendingShopName);
    } catch (error) {
      return null;
    }
  }

  function areNotificationsRead() {
    try {
      return sessionStorage.getItem(STORAGE_KEYS.notificationsRead) === "true";
    } catch (error) {
      return false;
    }
  }

  function markNotificationsRead() {
    try {
      sessionStorage.setItem(STORAGE_KEYS.notificationsRead, "true");
    } catch (error) {
      /* ignore */
    }
  }

  /* Demo data ----------------------------------------------------------- */
  function buildDemoFrames() {
    const now = Date.now();
    const demoFrames = [
      { id: "frame-aria", name: "Aria", shape: "Round", material: "Acetate", color: "Teal Fade", status: "live",
        description: "A lightweight round frame with a soft teal fade. Easy everyday wear." },
      { id: "frame-milo", name: "Milo", shape: "Square", material: "Metal", color: "Slate Blue", status: "live",
        description: "Clean square lines in brushed slate blue metal." },
      { id: "frame-nova", name: "Nova", shape: "Cat-Eye", material: "Acetate", color: "Rosewood", status: "pending",
        description: "Upswept cat-eye silhouette in a warm rosewood acetate." },
      { id: "frame-leo", name: "Leo", shape: "Aviator", material: "Titanium", color: "Warm Gold", status: "live",
        description: "Classic aviator in featherweight titanium with a warm gold finish." },
      { id: "frame-juno", name: "Juno", shape: "Rectangle", material: "Acetate", color: "Matte Black", status: "draft",
        description: "Slim rectangular frame in matte black. Still being photographed." },
    ];
    return demoFrames.map((frame, index) => ({
      ...frame,
      imageDataUrl: null,
      createdAt: now - index * ONE_DAY_IN_MS,
    }));
  }

  function ensureSeedData() {
    if (findAccountByEmail(DEMO_ACCOUNT_EMAIL)) {
      return;
    }
    const accounts = getAccounts();
    accounts.push({
      email: DEMO_ACCOUNT_EMAIL,
      password: DEMO_ACCOUNT_PASSWORD,
      shop: {
        name: "ClearView Optical",
        contactPerson: "Maria Santos",
        phone: "+63 917 123 4567",
        businessPermitNumber: "BP-2024-00817",
        address: "2F Ayala Mall, Cebu City",
        status: "approved",
        profileViews: 1204,
      },
    });
    saveAccounts(accounts);
    saveFrames(DEMO_ACCOUNT_EMAIL, buildDemoFrames());
  }

  return {
    DEMO_ACCOUNT_EMAIL,
    DEMO_ACCOUNT_PASSWORD,
    getAccounts,
    saveAccounts,
    findAccountByEmail,
    getSessionEmail,
    setSessionEmail,
    clearSession,
    loadFrames,
    saveFrames,
    setPendingShopName,
    getPendingShopName,
    areNotificationsRead,
    markNotificationsRead,
    ensureSeedData,
  };
})();
