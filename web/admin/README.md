# FaceFit AI — Administrator Web Management System

## Phase 1: Administrator Authentication + Base Admin Layout

Built against your existing Firebase project `facefit-ai-ca57f`, using the
Firebase JS SDK 12.18.0 via CDN `<script type="module">` — no npm, no bundler.

---

## Step 7 — Create Your First Administrator Account

Phase 1 has no "sign up" screen — administrator accounts are provisioned manually.

1. Firebase Console → **Authentication** → Users → **Add user** → enter an email +
   password (e.g. `admin@facefit.ai`). Copy the generated **User UID**.
2. Firebase Console → **Firestore Database** → Start collection → Collection ID: `admins`.
3. Document ID: **paste the User UID you copied exactly.**
4. Add these fields:

   | Field | Type | Value |
   |---|---|---|
   | `role` | string | `admin` |
   | `email` | string | `admin@facefit.ai` |
   | `createdAt` | timestamp | (now) |

5. Deploy the Firestore rules so this collection is actually protected in
   production mode (see Step 6 file `firestore.rules`):
   ```bash
   firebase deploy --only firestore:rules
   ```

> The `admins` collection is a minimal, auxiliary collection needed only to
> make role verification work — separate from your ERD's main collections
> (users, retailers, frame listings). We'll design those together once you
> share the latest ERD/Data Dictionary in Phase 2.

---

## Step 8 — Run Locally

ES module imports (`type="module"`) don't work over `file://` — serve the
folder over local HTTP. Since you already have a Firebase Hosting site
(`facefit-ai-ca57f`), the Firebase CLI is the most direct option:

```bash
npm install -g firebase-tools     # one-time, only needed for the CLI tool itself
firebase login
cd facefit-ai-web
firebase use facefit-ai-ca57f
firebase serve --only hosting
```

Visit: `http://localhost:5000/admin/login.html`

Alternative (no CLI): VS Code "Live Server" extension → right-click
`admin/login.html` → "Open with Live Server", or:

```bash
cd facefit-ai-web
python3 -m http.server 8000
```
Visit `http://localhost:8000/admin/login.html`

> `localhost` is authorized for Firebase Auth by default — no extra domain
> configuration needed for local testing.

---

## Step 9 — Testing Checklist

- [ ] Visiting `admin/dashboard.html` directly **without** logging in redirects
      you to `admin/login.html` (proves the route guard works).
- [ ] Logging in with the wrong password shows **"Incorrect email or password."**
- [ ] Logging in with an email that **has no `admins` document** shows
      **"This account is not authorized as an Administrator."** and does not
      enter the dashboard.
- [ ] Logging in with your provisioned admin account redirects to the Dashboard.
- [ ] Sidebar shows your admin email at the bottom.
- [ ] The four summary cards show **"—" placeholders**, not fake numbers.
- [ ] Clicking the logout icon (⏻) signs you out and returns you to the login page.
- [ ] After logout, browser Back button does **not** show the dashboard again.
- [ ] Resize the browser to mobile width — layout stays usable (sidebar/cards stack).

---

## Common Errors

| Error | Cause | Fix |
|---|---|---|
| Blank page, console says `Failed to load module script` | Opened the HTML file directly (`file://`) | Serve it over local HTTP — see Step 8. |
| `Firebase: Error (auth/invalid-api-key)` | Config mismatch | Re-check `firebase-config.js` matches the Firebase Console exactly. |
| Login succeeds but immediately kicks you back to login | No matching `admins/{uid}` document, or `role` isn't exactly `admin` | Re-check the UID matches exactly and the field value is lowercase `admin`. |
| `Missing or insufficient permissions` in console | Firestore rules not deployed yet | `firebase deploy --only firestore:rules` |
| Dashboard flashes then redirects to login | Normal if not signed in — the guard runs asynchronously | No action needed. |

---

## Project Structure

```
facefit-ai-web/
├── admin/
│   ├── login.html
│   └── dashboard.html
├── assets/
│   ├── css/
│   │   ├── global.css      ← Bootstrap variable overrides + brand tokens
│   │   └── admin.css       ← sidebar/topbar/login layout
│   ├── js/
│   │   ├── firebase-config.js   ← your real facefit-ai-ca57f config
│   │   ├── auth.js
│   │   ├── admin-guard.js
│   │   ├── login.js
│   │   └── dashboard.js
│   └── images/
├── firestore.rules
├── storage.rules
├── firebase.json           ← hosting site set to facefit-ai-ca57f
└── README.md
```

---

## Next Phase (waiting for your confirmation)

**Phase 2: Retailer Applications** — once you confirm Phase 1 works, share your
ERD/Data Dictionary and we'll design the retailer applications schema together
before building the list + review + approve/reject flow.
