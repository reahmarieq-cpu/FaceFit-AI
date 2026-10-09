# FaceFit AI UI refinement

This is an incremental visual and usability update to the uploaded Admin Web project, not a rebuild.

## Changes
- Refined desktop spacing, sidebar dimensions, card rhythm and focus styles.
- Improved narrow mobile layout and reduced-motion support.
- Kept the shared layout, page DOM IDs, page scripts, Firebase configuration, Firestore rules and callable functions.
- Fixed local emulator navigation: when opened with `?useEmulators=true`, internal sidebar links and login/logout redirects retain the flag. Production navigation does not acquire the flag.

## Test locally
1. From this folder run `py -m http.server 8000`.
2. Visit `http://localhost:8000/admin/login.html` for normal mode, or `http://localhost:8000/admin/login.html?useEmulators=true` with the emulator suite running.
3. Test navigation, search, authentication and CRUD/moderation actions on desktop and mobile.

## Limits
The changes are static-source checked, not browser-tested against your authenticated Firebase environment. Cloud Functions still require Blaze for production deployment.
