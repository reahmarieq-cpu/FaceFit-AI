/* ==========================================================================
   auth.js
   Sign in, registration, sign out and the "pending review" screen.
   ========================================================================== */

const FaceFitAuth = (() => {
  const LOGIN_PAGE = "index.html";
  const DASHBOARD_PAGE = "dashboard.html";
  const PENDING_PAGE = "pending.html";

  /* Core actions -------------------------------------------------------- */
  function signIn(email, password) {
    const account = FaceFitStorage.findAccountByEmail(email);
    if (!account || account.password !== password) {
      return { ok: false, message: "Incorrect email or password." };
    }
    if (account.shop.status !== "approved") {
      return {
        ok: false,
        variant: "warning",
        message: `${account.shop.name} is awaiting administrator approval. Frame management is unavailable until approval.`,
      };
    }
    FaceFitStorage.setSessionEmail(account.email);
    return { ok: true };
  }

  function registerShop(details) {
    const email = details.email.trim().toLowerCase();
    if (FaceFitStorage.findAccountByEmail(email)) {
      return { ok: false, message: "An account with this email already exists." };
    }

    const accounts = FaceFitStorage.getAccounts();
    accounts.push({
      email,
      password: details.password,
      shop: {
        name: details.shopName.trim(),
        ownerFirstName: details.ownerFirstName.trim(),
        ownerLastName: details.ownerLastName.trim(),
        contactPerson: `${details.ownerFirstName.trim()} ${details.ownerLastName.trim()}`,
        phone: details.phone.trim(),
        businessPermitNumber: details.businessPermit.trim(),
        address: details.address.trim(),
        addressParts: details.addressParts,
        businessPermit: details.permit,
        submittedAt: new Date().toISOString(),
        status: "pending",
        profileViews: 0,
      },
    });

    if (!FaceFitStorage.saveAccounts(accounts)) {
      return { ok: false, message: "We couldn't save your application. Check your browser storage and try again." };
    }
    FaceFitStorage.clearSession();
    FaceFitStorage.setPendingShopName(details.shopName.trim());
    return { ok: true };
  }

  function signOut() {
    FaceFitStorage.clearSession();
    window.location.href = LOGIN_PAGE;
  }

  /** Returns the signed-in, approved account, or redirects to the sign-in page. */
  function requireApprovedSession() {
    const account = FaceFitStorage.findAccountByEmail(FaceFitStorage.getSessionEmail() || "");
    if (!account || account.shop.status !== "approved") {
      FaceFitStorage.clearSession();
      if (account && account.shop.status === "pending") {
        FaceFitStorage.setPendingShopName(account.shop.name);
        window.location.replace(PENDING_PAGE);
      } else {
        window.location.replace(LOGIN_PAGE);
      }
      return null;
    }
    return account;
  }

  /* Page: sign in ------------------------------------------------------- */
  function initLoginPage() {
    const loginForm = document.getElementById("loginForm");
    const loginAlert = document.getElementById("loginAlert");
    const forgotPasswordForm = document.getElementById("forgotPasswordForm");
    const forgotPasswordEmail = document.getElementById("forgotPasswordEmail");

    const existingAccount = FaceFitStorage.findAccountByEmail(FaceFitStorage.getSessionEmail() || "");
    if (existingAccount && existingAccount.shop.status === "approved") {
      window.location.replace(DASHBOARD_PAGE);
      return;
    }

    loginForm.addEventListener("submit", (event) => {
      event.preventDefault();
      FaceFitUi.hideAlert(loginAlert);
      if (!FaceFitUi.validateForm(loginForm)) {
        return;
      }

      const result = signIn(
        document.getElementById("loginEmail").value,
        document.getElementById("loginPassword").value
      );
      if (result.ok) {
        window.location.href = DASHBOARD_PAGE;
      } else {
        FaceFitUi.showAlert(loginAlert, result.message, result.variant || "danger");
      }
    });

    forgotPasswordForm.addEventListener("submit", (event) => {
      event.preventDefault();
      if (!FaceFitUi.validateForm(forgotPasswordForm)) {
        return;
      }
      const enteredEmail = forgotPasswordEmail.value.trim();
      bootstrap.Modal.getInstance(document.getElementById("forgotPasswordModal")).hide();
      forgotPasswordForm.reset();
      forgotPasswordForm.classList.remove("was-validated");
      FaceFitUi.showToast(`If an account exists for ${enteredEmail}, a reset link is on its way.`);
    });
  }

  /* Page: register ------------------------------------------------------ */
  function initRegisterPage() {
    FaceFitRegistration.init(registerShop);
  }

  /* Page: application submitted ---------------------------------------- */
  function initPendingPage() {
    const submittedShopName = FaceFitStorage.getPendingShopName();
    if (submittedShopName) {
      document.getElementById("submittedShopName").textContent = submittedShopName;
    }

  }

  return { signOut, requireApprovedSession, initLoginPage, initRegisterPage, initPendingPage };
})();
