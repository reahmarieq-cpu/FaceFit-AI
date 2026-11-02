# FaceFit AI Admin — Figma dimension alignment

Design source: FaceFit AI — Wireframes, Figma file `jbinNcKTa6PQVgsoQa7STJ`.

Desktop frame IDs: login `98:2422`, dashboard `98:2513`, shop applications `98:2605`, frame review `98:2876`, user accounts `98:3157`, frame catalog `98:3332`.

All six frames are **1440 × 1024 CSS pixels**. Desktop styling is calibrated to that viewport, especially the shared shell. Use browser DevTools responsive mode at **1440 × 1024**, browser zoom **100%**, to compare.

Measured reference geometry:
- Sidebar about 263 px wide, full viewport height.
- Dashboard main header begins at x≈263 and is about 82 px tall.
- Sidebar navigation rows about 46 px high.
- Dashboard summary cards about 163 px tall.
- Login dark left panel about 890 px of 1440 px (61.8%).
- Login field controls about 475 × 55 px.

Changes in `assets/css/admin.css` are scoped to desktop (min-width: 992px), preserving the existing tablet/mobile drawer and stack behavior.

## Limitations
This pass corrects the main geometry using real Figma layer measurements, **not** a verified pixel-by-pixel rendering of every nested element. All Firebase and Cloud Functions source files are untouched. Visual comparison in an actual browser is still recommended, especially for logo assets, typography and individual table rows. Figma desktop frames are fixed reference canvases; responsive layouts intentionally adapt rather than remain 1440 px wide on smaller devices.
