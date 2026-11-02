/* ==========================================================================
   frame-icons.js
   Draws a simple SVG pair of glasses for a frame's shape and color, so the
   catalog looks right even when no photo has been uploaded.
   ========================================================================== */

const FaceFitFrameIcons = (() => {
  const DEFAULT_COLOR = "#1a9bad";

  // First keyword found in the color name decides the drawn color.
  const COLOR_KEYWORDS = [
    ["teal", "#1a9bad"], ["slate", "#3b78d8"], ["blue", "#3b78d8"], ["navy", "#2b4a8a"],
    ["rose", "#c4577c"], ["pink", "#d96b94"], ["gold", "#e0a23c"], ["yellow", "#e0a23c"],
    ["black", "#25313c"], ["tortoise", "#9a6a3a"], ["brown", "#8a5a3c"], ["red", "#cf4a4a"],
    ["green", "#2f9d6b"], ["silver", "#8c9aa6"], ["gray", "#8c9aa6"], ["grey", "#8c9aa6"],
    ["purple", "#7a5cc7"],
  ];

  const SHAPE_DRAWINGS = {
    "Round": `<circle cx="20" cy="21" r="14"/><circle cx="60" cy="21" r="14"/><path d="M34 18h12"/>`,
    "Oval": `<ellipse cx="20" cy="21" rx="16" ry="12"/><ellipse cx="60" cy="21" rx="16" ry="12"/><path d="M36 18h8"/>`,
    "Square": `<rect x="4" y="8" width="30" height="26" rx="5"/><rect x="46" y="8" width="30" height="26" rx="5"/><path d="M34 16h12"/>`,
    "Rectangle": `<rect x="4" y="11" width="32" height="20" rx="3"/><rect x="44" y="11" width="32" height="20" rx="3"/><path d="M36 17h8"/>`,
    "Cat-Eye": `<path d="M4 10l30 4q2 14-12 17Q8 32 5 22z"/><path d="M76 10l-30 4q-2 14 12 17 14 1 17-9z"/><path d="M34 16h12"/>`,
    "Aviator": `<path d="M4 12h32q0 20-16 21Q6 32 4 12z"/><path d="M44 12h32q-2 20-16 21-16-1-16-21z"/><path d="M36 14h8"/>`,
  };

  function colorFromName(colorName) {
    const lowerCaseName = String(colorName || "").toLowerCase();
    const match = COLOR_KEYWORDS.find(([keyword]) => lowerCaseName.includes(keyword));
    return match ? match[1] : DEFAULT_COLOR;
  }

  /** Soft background tint for the thumbnail behind the glasses. */
  function tintFromName(colorName) {
    return `${colorFromName(colorName)}26`;
  }

  function renderSvg(shape, colorName) {
    const drawing = SHAPE_DRAWINGS[shape] || SHAPE_DRAWINGS["Round"];
    return `<svg viewBox="0 0 80 42" fill="none" stroke="${colorFromName(colorName)}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${drawing}</svg>`;
  }

  /** Uploaded photo when present, otherwise the drawn glasses. */
  function renderVisual(frame) {
    const hasPhoto = typeof frame.imageDataUrl === "string" && frame.imageDataUrl.startsWith("data:image/");
    if (hasPhoto) {
      return `<img src="${frame.imageDataUrl}" alt="${FaceFitUi.escapeHtml(frame.name)}">`;
    }
    return renderSvg(frame.shape, frame.color);
  }

  return { renderSvg, renderVisual, tintFromName };
})();
