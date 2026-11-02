# Temporary approval from Admin Web (Spark plan)

The Admin Web Approve/Reject retailer and Approve/Hide frame buttons now use a Firestore transaction instead of callable functions. This is a **temporary development mode**; `TEMPORARY_DIRECT_APPROVAL = true` in both `assets/js/retailer-detail.js` and `assets/js/frame-listing-detail.js`.

## Required deployment

From the folder containing `firebase.json`, verify the selected project is `facefit-ai-ca57f` and run:

```powershell
firebase.cmd deploy --only firestore:rules,hosting --project facefit-ai-ca57f
```

**Security:** The rules allow only Firebase-authenticated users with `admins/{uid}.role == "admin"` to change `status`, and only from `pending` to `approved`/`rejected` for retailers or `approved`/`hidden` for frames. No other fields, creation, or deletion are allowed. Never relax the admin checks. Existing rules remain unchanged elsewhere.

**Limitations:** This temporary path does NOT generate notifications, and it does not invoke Cloud Functions. Real production data is modified when buttons are clicked. Use a test application first. Once Blaze is enabled and functions are deployed, change both `TEMPORARY_DIRECT_APPROVAL` constants to `false`, and restore the original no-client-write retailer/frame rules before deploying the final release. Ensure the future retailer portal registration rules are coordinated separately.

If a click returns `permission-denied`, check that Firestore rules were deployed and your signed-in account has a matching `admins/{uid}` document with `role: "admin"`. The frontend's own guard alone is not sufficient; the rules enforce authorization.
