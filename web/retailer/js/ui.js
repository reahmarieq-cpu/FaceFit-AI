/* ==========================================================================
   ui.js
   Small shared helpers: escaping, initials, toasts, alerts, form validation.
   ========================================================================== */

const FaceFitUi = (() => {
  const HTML_ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };

  function escapeHtml(text) {
    return String(text ?? "").replace(/[&<>"']/g, (character) => HTML_ESCAPES[character]);
  }

  /** "ClearView Optical" -> "CV" (first two capital letters). */
  function getInitials(name) {
    const capitals = String(name).match(/[A-Z]/g) || [];
    if (capitals.length >= 2) {
      return capitals.slice(0, 2).join("");
    }
    return String(name).trim().slice(0, 2).toUpperCase() || "FF";
  }

  function showToast(message, variant = "success") {
    let toastRegion = document.getElementById("toastRegion");
    if (!toastRegion) {
      toastRegion = document.createElement("div");
      toastRegion.id = "toastRegion";
      toastRegion.className = "toast-region";
      document.body.appendChild(toastRegion);
    }

    const toastElement = document.createElement("div");
    toastElement.className = `toast align-items-center text-bg-${variant} border-0`;
    toastElement.setAttribute("role", "alert");
    toastElement.setAttribute("aria-live", "assertive");
    toastElement.setAttribute("aria-atomic", "true");

    const wrapper = document.createElement("div");
    wrapper.className = "d-flex";
    const body = document.createElement("div");
    body.className = "toast-body";
    body.textContent = message;
    const closeButton = document.createElement("button");
    closeButton.type = "button";
    closeButton.className = "btn-close btn-close-white me-2 m-auto";
    closeButton.setAttribute("data-bs-dismiss", "toast");
    closeButton.setAttribute("aria-label", "Close");
    wrapper.append(body, closeButton);
    toastElement.appendChild(wrapper);
    toastRegion.appendChild(toastElement);

    toastElement.addEventListener("hidden.bs.toast", () => toastElement.remove());
    new bootstrap.Toast(toastElement, { delay: 3200 }).show();
  }

  function showAlert(alertElement, message, variant = "danger") {
    alertElement.className = `form-alert alert alert-${variant}`;
    alertElement.textContent = message;
  }

  function hideAlert(alertElement) {
    alertElement.className = "form-alert alert d-none";
    alertElement.textContent = "";
  }

  /** Turns on Bootstrap validation styling and reports whether the form is valid. */
  function validateForm(formElement) {
    formElement.classList.add("was-validated");
    return formElement.checkValidity();
  }

  return { escapeHtml, getInitials, showToast, showAlert, hideAlert, validateForm };
})();
