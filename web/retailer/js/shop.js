/* ==========================================================================
   shop.js
   Everything behind the sign-in: shared layout behavior (sidebar, search,
   notifications) plus the dashboard, catalog, add/edit frame and profile pages.
   ========================================================================== */

const FaceFitShop = (() => {
  const MAX_IMAGE_SIZE_IN_BYTES = 1.5 * 1024 * 1024;
  const ACCEPTED_IMAGE_TYPES = ["image/png", "image/jpeg"];
  const RECENT_FRAME_COUNT = 4;
  const STATUS_LABELS = { live: "Live", pending: "Pending", draft: "Draft" };

  let currentAccount = null;

  /* Helpers ------------------------------------------------------------- */
  function getFrames() {
    return FaceFitStorage.loadFrames(currentAccount.email);
  }

  function persistFrames(frames) {
    return FaceFitStorage.saveFrames(currentAccount.email, frames);
  }

  function buildStatusBadge(status) {
    const label = STATUS_LABELS[status] || "Draft";
    return `<span class="status-badge status-badge--${FaceFitUi.escapeHtml(status)}">${label}</span>`;
  }

  function byNewestFirst(firstFrame, secondFrame) {
    return secondFrame.createdAt - firstFrame.createdAt;
  }

  /* Shared layout ------------------------------------------------------- */
  function initShell() {
    const sidebar = document.getElementById("appSidebar");
    const backdrop = document.getElementById("sidebarBackdrop");

    function setSidebarOpen(isOpen) {
      sidebar.classList.toggle("is-open", isOpen);
      backdrop.classList.toggle("is-visible", isOpen);
    }
    document.getElementById("sidebarToggle").addEventListener("click", () => setSidebarOpen(true));
    backdrop.addEventListener("click", () => setSidebarOpen(false));
    sidebar.querySelectorAll(".sidebar-link").forEach((link) => {
      link.addEventListener("click", () => setSidebarOpen(false));
    });

    document.getElementById("shopName").textContent = currentAccount.shop.name;
    document.getElementById("shopInitials").textContent = FaceFitUi.getInitials(currentAccount.shop.name);
    document.getElementById("logoutButton").addEventListener("click", FaceFitAuth.signOut);

    initNotifications();
    initTopbarSearch();
  }

  function initNotifications() {
    const notificationList = document.getElementById("notificationList");
    const notificationDot = document.getElementById("notificationDot");
    const pendingFrames = getFrames().filter((frame) => frame.status === "pending");

    const messages = [`${currentAccount.shop.name} is approved and live on FaceFit AI.`];
    pendingFrames.forEach((frame) => messages.push(`${frame.name} is waiting for administrator review.`));

    notificationList.innerHTML =
      `<li><h6 class="dropdown-header">Notifications</h6></li>` +
      messages.map((message) => `<li><span class="dropdown-item">${FaceFitUi.escapeHtml(message)}</span></li>`).join("");

    notificationDot.classList.toggle("d-none", FaceFitStorage.areNotificationsRead());
    document.getElementById("notificationButton").addEventListener("show.bs.dropdown", () => {
      FaceFitStorage.markNotificationsRead();
      notificationDot.classList.add("d-none");
    });
  }

  function initTopbarSearch() {
    const searchForm = document.getElementById("topbarSearchForm");
    const searchInput = document.getElementById("topbarSearchInput");
    const isCatalogPage = document.body.dataset.page === "catalog";

    searchForm.addEventListener("submit", (event) => {
      event.preventDefault();
      if (!isCatalogPage) {
        window.location.href = `catalog.html?q=${encodeURIComponent(searchInput.value.trim())}`;
      }
    });
  }

  /* Page: dashboard ----------------------------------------------------- */
  function initDashboardPage() {
    const frames = getFrames();
    const countByStatus = (status) => frames.filter((frame) => frame.status === status).length;

    document.getElementById("statTotalFrames").textContent = frames.length;
    document.getElementById("statLiveFrames").textContent = countByStatus("live");
    document.getElementById("statPendingFrames").textContent = countByStatus("pending");
    document.getElementById("statProfileViews").textContent = (currentAccount.shop.profileViews || 0).toLocaleString();
    document.getElementById("bannerShopName").textContent = currentAccount.shop.name;

    const recentFrames = [...frames].sort(byNewestFirst).slice(0, RECENT_FRAME_COUNT);
    const tableBody = document.getElementById("recentFramesBody");

    if (recentFrames.length === 0) {
      tableBody.innerHTML = `<tr><td colspan="4" class="text-center text-secondary py-4">No frames yet. Add your first frame to get started.</td></tr>`;
      return;
    }

    tableBody.innerHTML = recentFrames.map((frame) => `
      <tr>
        <td>
          <div class="frame-cell">
            <span class="frame-thumb" style="background:${FaceFitFrameIcons.tintFromName(frame.color)}">${FaceFitFrameIcons.renderVisual(frame)}</span>
            <div>
              <p class="frame-cell__name">${FaceFitUi.escapeHtml(frame.name)}</p>
              <p class="frame-cell__color">${FaceFitUi.escapeHtml(frame.color)}</p>
            </div>
          </div>
        </td>
        <td>${FaceFitUi.escapeHtml(frame.shape)}</td>
        <td>${FaceFitUi.escapeHtml(frame.material)}</td>
        <td>${buildStatusBadge(frame.status)}</td>
      </tr>`).join("");
  }

  /* Page: catalog ------------------------------------------------------- */
  function initCatalogPage() {
    const frameGrid = document.getElementById("frameGrid");
    const emptyState = document.getElementById("catalogEmptyState");
    const searchInput = document.getElementById("topbarSearchInput");
    const filterPills = document.querySelectorAll("[data-status-filter]");
    const deleteModalElement = document.getElementById("deleteFrameModal");
    const deleteModal = new bootstrap.Modal(deleteModalElement);

    const filters = {
      status: "all",
      searchText: new URLSearchParams(window.location.search).get("q") || "",
    };
    let frameIdPendingDelete = null;
    searchInput.value = filters.searchText;

    function getVisibleFrames() {
      const searchText = filters.searchText.trim().toLowerCase();
      return getFrames()
        .filter((frame) => filters.status === "all" || frame.status === filters.status)
        .filter((frame) => {
          const searchableText = `${frame.name} ${frame.shape} ${frame.material} ${frame.color}`.toLowerCase();
          return searchableText.includes(searchText);
        })
        .sort(byNewestFirst);
    }

    function renderFrames() {
      const visibleFrames = getVisibleFrames();
      emptyState.classList.toggle("d-none", visibleFrames.length > 0);

      frameGrid.innerHTML = visibleFrames.map((frame) => `
        <div class="col-12 col-sm-6 col-xl-4">
          <article class="frame-card">
            <div class="frame-card__visual" style="background:${FaceFitFrameIcons.tintFromName(frame.color)}">${FaceFitFrameIcons.renderVisual(frame)}</div>
            <div class="frame-card__head">
              <h3 class="frame-card__name">${FaceFitUi.escapeHtml(frame.name)}</h3>
              ${buildStatusBadge(frame.status)}
            </div>
            <p class="frame-card__color">${FaceFitUi.escapeHtml(frame.color)}</p>
            <div class="frame-card__tags">
              <span class="frame-tag">${FaceFitUi.escapeHtml(frame.shape)}</span>
              <span class="frame-tag">${FaceFitUi.escapeHtml(frame.material)}</span>
            </div>
            <p class="frame-card__description">${FaceFitUi.escapeHtml(frame.description || "No description yet.")}</p>
            <div class="frame-card__actions">
              <a class="btn-card-action text-center text-decoration-none" href="add-frame.html?id=${encodeURIComponent(frame.id)}">Edit</a>
              <button type="button" class="btn-card-action btn-card-action--danger" data-delete-frame-id="${FaceFitUi.escapeHtml(frame.id)}">Delete</button>
            </div>
          </article>
        </div>`).join("");
    }

    filterPills.forEach((pill) => {
      pill.addEventListener("click", () => {
        filters.status = pill.dataset.statusFilter;
        filterPills.forEach((otherPill) => otherPill.classList.toggle("is-active", otherPill === pill));
        renderFrames();
      });
    });

    searchInput.addEventListener("input", () => {
      filters.searchText = searchInput.value;
      renderFrames();
    });

    frameGrid.addEventListener("click", (event) => {
      const deleteButton = event.target.closest("[data-delete-frame-id]");
      if (!deleteButton) {
        return;
      }
      frameIdPendingDelete = deleteButton.dataset.deleteFrameId;
      const frame = getFrames().find((candidate) => candidate.id === frameIdPendingDelete);
      document.getElementById("deleteFrameName").textContent = frame ? frame.name : "this frame";
      deleteModal.show();
    });

    document.getElementById("confirmDeleteButton").addEventListener("click", () => {
      persistFrames(getFrames().filter((frame) => frame.id !== frameIdPendingDelete));
      deleteModal.hide();
      renderFrames();
      FaceFitUi.showToast("Frame deleted.");
    });

    renderFrames();
  }

  /* Page: add / edit frame --------------------------------------------- */
  function initAddFramePage() {
    const form = document.getElementById("addFrameForm");
    const nameInput = document.getElementById("frameName");
    const shapeSelect = document.getElementById("frameShape");
    const materialInput = document.getElementById("frameMaterial");
    const colorInput = document.getElementById("frameColor");
    const descriptionInput = document.getElementById("frameDescription");

    const dropzone = document.getElementById("imageDropzone");
    const imageInput = document.getElementById("frameImageInput");
    const imageFileName = document.getElementById("imageFileName");
    const imageError = document.getElementById("imageError");

    const previewStage = document.getElementById("previewStage");
    const previewName = document.getElementById("previewName");
    const previewMeta = document.getElementById("previewMeta");

    const editedFrameId = new URLSearchParams(window.location.search).get("id");
    const editedFrame = editedFrameId ? getFrames().find((frame) => frame.id === editedFrameId) : null;
    let selectedImageDataUrl = editedFrame ? editedFrame.imageDataUrl : null;

    function updatePreview() {
      const shape = shapeSelect.value;
      const colorName = colorInput.value.trim();
      previewName.textContent = nameInput.value.trim() || "Frame name";
      previewMeta.textContent = `${shape} · ${colorName || "Color"}`;
      previewStage.innerHTML = FaceFitFrameIcons.renderVisual({
        name: nameInput.value.trim() || "Frame preview",
        shape,
        color: colorName || "Teal",
        imageDataUrl: selectedImageDataUrl,
      });
    }

    function showImageError(message) {
      imageError.textContent = message;
      imageError.classList.toggle("d-none", !message);
    }

    function handleImageFile(file) {
      showImageError("");
      if (!file) {
        return;
      }
      if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
        showImageError("Please choose a PNG or JPG image.");
        return;
      }
      if (file.size > MAX_IMAGE_SIZE_IN_BYTES) {
        showImageError("Image is too large. Please keep it under 1.5 MB.");
        return;
      }

      const reader = new FileReader();
      reader.addEventListener("load", () => {
        selectedImageDataUrl = reader.result;
        imageFileName.textContent = file.name;
        imageFileName.classList.remove("d-none");
        updatePreview();
      });
      reader.readAsDataURL(file);
    }

    // Pre-fill when editing an existing frame.
    if (editedFrame) {
      nameInput.value = editedFrame.name;
      shapeSelect.value = editedFrame.shape;
      materialInput.value = editedFrame.material;
      colorInput.value = editedFrame.color;
      descriptionInput.value = editedFrame.description || "";
      document.title = "Edit Frame · FaceFit AI Shop";
      document.querySelector(".topbar-heading__title").textContent = "Edit Frame";
      document.querySelector(".topbar-heading__subtitle").textContent = "Update your listing. It will be re-reviewed.";
      form.querySelector("button[type='submit']").textContent = "Save & resubmit";
      form.querySelector(".surface-card__title").textContent = "Edit frame details";
      form.querySelector(".eyebrow").textContent = "Existing listing";
    }

    [nameInput, colorInput, descriptionInput].forEach((field) => field.addEventListener("input", updatePreview));
    shapeSelect.addEventListener("change", updatePreview);

    imageInput.addEventListener("change", () => handleImageFile(imageInput.files[0]));
    ["dragenter", "dragover"].forEach((eventName) => {
      dropzone.addEventListener(eventName, (event) => {
        event.preventDefault();
        dropzone.classList.add("is-dragover");
      });
    });
    ["dragleave", "drop"].forEach((eventName) => {
      dropzone.addEventListener(eventName, (event) => {
        event.preventDefault();
        dropzone.classList.remove("is-dragover");
      });
    });
    dropzone.addEventListener("drop", (event) => handleImageFile(event.dataTransfer.files[0]));

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (!FaceFitUi.validateForm(form)) {
        return;
      }

      const frames = getFrames();
      const submittedFrame = {
        id: editedFrame ? editedFrame.id : `frame-${Date.now()}`,
        name: nameInput.value.trim(),
        shape: shapeSelect.value,
        material: materialInput.value.trim(),
        color: colorInput.value.trim(),
        description: descriptionInput.value.trim(),
        imageDataUrl: selectedImageDataUrl,
        status: "pending",
        createdAt: editedFrame ? editedFrame.createdAt : Date.now(),
      };

      const updatedFrames = editedFrame
        ? frames.map((frame) => (frame.id === editedFrame.id ? submittedFrame : frame))
        : [submittedFrame, ...frames];

      if (!persistFrames(updatedFrames)) {
        FaceFitUi.showToast("Couldn't save. Try a smaller image.", "danger");
        return;
      }
      FaceFitUi.showToast(`${submittedFrame.name} was submitted for review.`);
      setTimeout(() => { window.location.href = "catalog.html"; }, 800);
    });

    updatePreview();
  }

  /* Page: shop profile -------------------------------------------------- */
  function initProfilePage() {
    const form = document.getElementById("shopProfileForm");
    const fields = {
      name: document.getElementById("profileShopName"),
      contactPerson: document.getElementById("profileContactPerson"),
      phone: document.getElementById("profilePhone"),
      businessPermitNumber: document.getElementById("profilePermit"),
      address: document.getElementById("profileAddress"),
    };
    const emailInput = document.getElementById("profileEmail");

    function renderProfileHeader() {
      document.getElementById("profileAvatar").textContent = FaceFitUi.getInitials(currentAccount.shop.name);
      document.getElementById("profileHeading").textContent = currentAccount.shop.name;
    }

    Object.entries(fields).forEach(([key, input]) => { input.value = currentAccount.shop[key] || ""; });
    emailInput.value = currentAccount.email;
    renderProfileHeader();

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (!FaceFitUi.validateForm(form)) {
        return;
      }

      const accounts = FaceFitStorage.getAccounts();
      const storedAccount = accounts.find((account) => account.email === currentAccount.email);
      ["name", "contactPerson", "phone", "address"].forEach((key) => {
        storedAccount.shop[key] = fields[key].value.trim();
      });
      FaceFitStorage.saveAccounts(accounts);

      currentAccount = storedAccount;
      document.getElementById("shopName").textContent = currentAccount.shop.name;
      document.getElementById("shopInitials").textContent = FaceFitUi.getInitials(currentAccount.shop.name);
      renderProfileHeader();
      form.classList.remove("was-validated");
      FaceFitUi.showToast("Shop profile saved.");
    });
  }

  /* Entry point --------------------------------------------------------- */
  function initPage(pageName) {
    currentAccount = FaceFitAuth.requireApprovedSession();
    if (!currentAccount) {
      return;
    }
    initShell();

    const pageInitializers = {
      "dashboard": initDashboardPage,
      "catalog": initCatalogPage,
      "add-frame": initAddFramePage,
      "profile": initProfilePage,
    };
    pageInitializers[pageName]();
  }

  return { initPage };
})();
