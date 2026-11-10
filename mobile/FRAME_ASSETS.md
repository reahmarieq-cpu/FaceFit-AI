# Local frame assets

Existing filenames were preserved, including case and thumbnail spelling. Android asset paths are case-sensitive.

| Catalog name | Thumbnail | Try-on |
| --- | --- | --- |
| Pax | frames/pax/pax.png | Same PNG, eye anchors measured from lens openings |
| Square Tortoise | frames/square_tortoise/tumbnail.png | frames/square_tortoise/SquareTortoise.glb |
| Nova (Cat-Eye) | frames/cat-eye/thumbail.png | frames/cat-eye/model.glb |
| Luna (Round) | frames/Round/thumbnail.png | frames/Round/model.glb |
| Browline Black | frames/browline/black_browline_front_depth.png | frames/browline/black_browline_front_layer.png plus black_browline_temple.png in the same folder |

Milo and Leo have no supplied assets and retain their existing illustrated placeholders with try-on unavailable.
Catalog names remain unchanged to preserve existing favorites. Scores remain sample scores. New model material
and physical dimensions are not verified; the model metadata says dimensions are estimated and Android VTO untested.
The Cat-Eye and Round fit profiles use their exported anchor_lens_image_left/right nodes. Phone fit still needs testing.
No PNG/GLB files were moved, renamed or modified. Thumbnails are now separate from 3D asset paths so catalog,
Favorites and details show photos instead of attempting to decode a GLB as a bitmap.

For future frames, use a lowercase folder such as frames/round_black/ containing thumbnail.png and either
model.glb or a front.png image for 2D try-on. Add one FrameCatalog.kt entry. Do not use angled product photos
as front-facing PNG try-on layers; front.png should have transparent background and open lens regions.

Files changed for this connection:
- FrameCatalog.kt: fixed moved paths, registered new GLBs/photos, separate thumbnail path, PNG try-on flags and anchors.
- FrameMatchesScreen.kt: shared thumbnail rendering used by catalog, Favorites and details.
- VtoAutomaticFit.kt: new GLB lens-anchor profiles and updated Square Tortoise path.
- FilamentVtoTest.kt, Glasses3DRenderingTest.kt, SquareTortoiseAssetTest.kt: updated moved test asset paths.
- FrameCatalogAssetsTest.kt: added asset existence/PNG routing/exported-anchor checks.
- FRAME_ASSETS.md: this organization guide.

## Rectangle Rose Gold: layered image try-on

Uses the original `frames/rectangle/model.png` as its catalog photo. `front`, `arm_image_left`, and
`arm_image_right` are valid transparent PNG files without extensions; their names/content remain unchanged.
Lens centers are measured from front-layer openings. Each arm has independent hinge, tip and width anchors.
The renderer projects the front as a perspective plane, transforms original arm pixels and masks the far arm.
Ear placement is an estimate: this is 2.5D for moderate turns, not an exact 3D reconstruction or physical fit.
Original cutout fringes are preserved rather than repainted. Strong profiles still require side reference images/3D.

Layered connection changes: FrameCatalog.kt (entry, anchor metadata, and repaired malformed Square Tortoise path),
VirtualTryOnScreen.kt (load/route separate arms), LayeredImageTryOnOverlay.kt (new textured layers renderer),
LayeredImageGeometry.kt (projection), LayeredImageGeometryTest.kt (geometry tests),
FrameCatalogAssetsTest.kt (extensionless PNG and arm-asset validation), this guide.
