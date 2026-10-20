/* Registration controller. Applications remain pending in the local demo store. */
const FaceFitRegistration = (() => {
  // Numbering-plan validation uses the vendored libphonenumber-js max metadata.
  // A valid format does not establish ownership of a number.
  function isValidNationalNumber(digits, country, callingCode) {
    if (typeof libphonenumber === "undefined") return false;
    const parsed = libphonenumber.parsePhoneNumberFromString(`+${callingCode}${digits}`);
    return Boolean(parsed && parsed.country === country && parsed.isValid());
  }

  function cleanPhoneDigits(value, country, callingCode) {
    if (!/^\d*$/.test(value)) return null;
    let digits = value.replace(/^0+/, "");
    // Accept an already-prefixed number only if it resolves to the selected country.
    if (!isValidNationalNumber(digits, country, callingCode) && digits.startsWith(callingCode)) {
      const national = digits.slice(callingCode.length).replace(/^0+/, "");
      if (isValidNationalNumber(national, country, callingCode)) digits = national;
    }
    return digits;
  }

  function normalizePhone(value, country, callingCode) {
    const digits = cleanPhoneDigits(value, country, callingCode);
    if (!digits || !isValidNationalNumber(digits, country, callingCode)) return null;
    return `+${callingCode}${digits}`;
  }

  function init(registerShop) {
    const form = document.getElementById("registerForm");
    const business = document.getElementById("businessStep");
    const account = document.getElementById("accountStep");
    const alert = document.getElementById("registerAlert");
    const password = document.getElementById("registerPassword");
    const confirm = document.getElementById("registerPasswordConfirm");
    const permit = document.getElementById("permitFile");
    const submit = document.getElementById("submitApplicationButton");
    const phone = document.getElementById("shopPhone");
    const phoneCountry = document.getElementById("phoneCountry");
    let phoneInputError = "";
    let phoneTouched = false;

    function validatePhone() {
      const code = phoneCountry.selectedOptions[0].dataset.code;
      const normalized = normalizePhone(phone.value, phoneCountry.value, code);
      const countryName = phoneCountry.selectedOptions[0].textContent.split(" (+")[0];
      const message = phoneInputError || (typeof libphonenumber === "undefined"
        ? "Phone validation could not load. Please reload this page."
        : !phone.value ? "Enter your contact number."
        : `Enter a valid ${countryName} number for +${code}. Check its length and digits.`);
      const valid = Boolean(normalized) && !phoneInputError;
      phone.setCustomValidity(valid ? "" : message);
      document.getElementById("phoneFeedback").textContent = valid ? "" : message;
      const showError = (phoneTouched || business.classList.contains("was-validated")) && !valid;
      phone.closest(".phone-field").classList.toggle("phone-field--invalid", showError);
      phone.setAttribute("aria-invalid", String(showError));
      return valid ? normalized : null;
    }
    function updatePhoneCountry() {
      const code = phoneCountry.selectedOptions[0].dataset.code;
      const isPhilippines = phoneCountry.value === "PH";
      document.getElementById("phoneCallingCode").textContent = `+${code}`;
      phone.placeholder = isPhilippines ? "9123456789" : "Enter national number";
      const phoneHint = document.getElementById("phoneHint");
      if (phoneHint) phoneHint.textContent = `Digits only. +${code} is added automatically; a leading 0 is removed.`;
      phoneInputError = "";
      validatePhone();
    }
    phone.addEventListener("input", () => {
      phoneTouched = true;
      phoneInputError = /\D/.test(phone.value) ? "Use digits only. Do not enter spaces, letters, or symbols in the phone number." : "";
      const code = phoneCountry.selectedOptions[0].dataset.code;
      phone.value = cleanPhoneDigits(phone.value.replace(/\D/g, ""), phoneCountry.value, code);
      validatePhone();
    });
    phone.addEventListener("blur", () => { phoneTouched = true; validatePhone(); });
    phone.addEventListener("paste", event => {
      event.preventDefault();
      const pasted = event.clipboardData.getData("text");
      const code = phoneCountry.selectedOptions[0].dataset.code;
      phoneTouched = true;
      // A single matching international prefix is accepted on a full-number paste.
      const international = /^\+\d+$/.test(pasted);
      if (international && !pasted.startsWith(`+${code}`)) {
        phoneInputError = "Choose the country matching the pasted number's calling code.";
      } else if (!/^\d+$/.test(pasted) && !international) {
        phoneInputError = "Use digits only, with no spaces, letters, or symbols.";
      } else {
        phoneInputError = "";
        const digits = international ? pasted.slice(code.length + 1) : pasted;
        const next = international ? digits : phone.value.slice(0, phone.selectionStart) + digits + phone.value.slice(phone.selectionEnd);
        // Do not strip another country code after removing an explicit + prefix.
        phone.value = international ? next.replace(/^0+/, "") : cleanPhoneDigits(next, phoneCountry.value, code);
        if (international && !isValidNationalNumber(phone.value, phoneCountry.value, code)) {
          phoneInputError = "The pasted number is invalid or contains a duplicate country code. Enter the national number only.";
        }
      }
      validatePhone();
    });
    phoneCountry.addEventListener("change", updatePhoneCountry);
    let busy = false;

    // Reset browser-restored entries, including back/forward navigation.
    function reset() {
      form.reset();
      phoneInputError = "";
      phoneTouched = false;
      form.querySelectorAll("input:not([type=file]), textarea").forEach(input => { input.value = ""; });
      form.querySelectorAll("input").forEach(input => input.setCustomValidity(""));
      updatePhoneCountry();
      form.querySelectorAll("[data-password-target]").forEach(button => {
        document.getElementById(button.dataset.passwordTarget).type = "password";
        button.setAttribute("aria-pressed", "false");
        button.setAttribute("aria-label", button.dataset.passwordTarget === "registerPassword" ? "Show password" : "Show confirm password");
        button.firstElementChild.className = "bi bi-eye";
      });
      business.classList.remove("was-validated");
      account.classList.remove("was-validated");
      phone.closest(".phone-field").classList.remove("phone-field--invalid");
      phone.setAttribute("aria-invalid", "false");
      document.getElementById("permitStatus").textContent = "";
      FaceFitUi.hideAlert(alert);
      showStep(1, false);
    }

    function showStep(step, focus = true) {
      business.hidden = step !== 1;
      business.disabled = step !== 1;
      account.hidden = step !== 2;
      account.disabled = step !== 2;
      document.getElementById("businessStepIndicator").toggleAttribute("aria-current", step === 1);
      document.getElementById("accountStepIndicator").toggleAttribute("aria-current", step === 2);
      document.querySelector(".registration-steps [aria-current]").setAttribute("aria-current", "step");
      if (focus) (step === 1 ? business : account).querySelector("legend").focus();
    }

    function validatePermit() {
      const file = permit.files[0];
      const valid = file && ["application/pdf", "image/jpeg", "image/png"].includes(file.type) && file.size > 0 && file.size <= 2 * 1024 * 1024;
      permit.setCustomValidity(valid ? "" : "Upload a PDF, JPG, or PNG, up to 2 MB.");
      return valid;
    }
    permit.addEventListener("change", () => {
      const valid = validatePermit();
      document.getElementById("permitStatus").textContent = valid ? `Selected: ${permit.files[0].name}` : "Choose a PDF, JPG, or PNG file, up to 2 MB.";
    });
    function validateBusiness() {
      business.querySelectorAll("input[type=text], textarea").forEach(input => { input.value = input.value.trim(); });
      validatePermit();
      business.classList.add("was-validated");
      validatePhone();
      const invalid = business.querySelector(":invalid");
      if (invalid) invalid.focus();
      return !invalid;
    }
    document.getElementById("nextStepButton").addEventListener("click", () => {
      FaceFitUi.hideAlert(alert);
      if (validateBusiness()) showStep(2);
    });
    document.getElementById("backStepButton").addEventListener("click", () => {
      FaceFitUi.hideAlert(alert);
      showStep(1);
    });
    function checkPasswords() {
      confirm.setCustomValidity(confirm.value === password.value ? "" : "Passwords must match.");
    }
    password.addEventListener("input", checkPasswords);
    confirm.addEventListener("input", checkPasswords);
    form.querySelectorAll("[data-password-target]").forEach(button => {
      button.addEventListener("click", () => {
        const input = document.getElementById(button.dataset.passwordTarget);
        const show = input.type === "password";
        input.type = show ? "text" : "password";
        button.setAttribute("aria-pressed", String(show));
        button.setAttribute("aria-label", `${show ? "Hide" : "Show"} ${input === confirm ? "confirm password" : "password"}`);
        button.firstElementChild.className = show ? "bi bi-eye-slash" : "bi bi-eye";
      });
    });
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (busy) return;
      if (!business.hidden) {
        if (validateBusiness()) showStep(2);
        return;
      }
      FaceFitUi.hideAlert(alert);
      checkPasswords();
      document.getElementById("shopEmail").value = document.getElementById("shopEmail").value.trim();
      account.classList.add("was-validated");
      const invalid = account.querySelector(":invalid");
      if (invalid) { invalid.focus(); return; }
      // Disabled fieldsets do not participate in native validation.
      business.disabled = false;
      const valid = validateBusiness();
      business.disabled = true;
      if (!valid) { showStep(1); return; }
      busy = true;
      submit.disabled = true;
      document.getElementById("backStepButton").disabled = true;
      submit.textContent = "Submitting…";
      form.setAttribute("aria-busy", "true");
      try {
        const file = permit.files[0];
        const dataUrl = await new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result);
          reader.onerror = reader.onabort = () => reject(new Error("Could not read your permit. Please select the file again."));
          reader.readAsDataURL(file);
        });
        const addressParts = {
          unit: document.getElementById("addressUnit").value.trim(),
          street: document.getElementById("addressStreet").value.trim(),
          barangay: document.getElementById("addressBarangay").value.trim(),
          city: document.getElementById("addressCity").value.trim(),
          province: document.getElementById("addressProvince").value.trim(),
          postalCode: document.getElementById("addressPostalCode").value.trim(),
          country: document.getElementById("addressCountry").value.trim(),
        };
        const result = registerShop({
          shopName: document.getElementById("shopName").value,
          ownerFirstName: document.getElementById("ownerFirstName").value,
          ownerLastName: document.getElementById("ownerLastName").value,
          phone: validatePhone(),
          businessPermit: document.getElementById("businessPermit").value,
          address: Object.values(addressParts).filter(Boolean).join(", "),
          addressParts,
          email: document.getElementById("shopEmail").value,
          password: password.value,
          permit: { name: file.name, type: file.type, size: file.size, dataUrl },
        });
        if (result.ok) window.location.assign("pending.html");
        else FaceFitUi.showAlert(alert, result.message);
      } catch (error) {
        FaceFitUi.showAlert(alert, error.message || "Unable to submit your application. Please try again.");
      } finally {
        busy = false;
        submit.disabled = false;
        document.getElementById("backStepButton").disabled = false;
        submit.textContent = "Submit Application";
        form.removeAttribute("aria-busy");
      }
    });
    reset();
    window.addEventListener("pageshow", event => { if (event.persisted) reset(); });
  }
  return { init, normalizePhone, cleanPhoneDigits };
})();
