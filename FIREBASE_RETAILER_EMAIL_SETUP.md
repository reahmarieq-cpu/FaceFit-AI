# FaceFit AI — Retailer decision email

The `queueRetailerDecisionEmail` Cloud Function watches `retailers/{retailerId}`. A transition from pending to approved or rejected queues one message to the retailer's Firebase Auth email address. The event ID prevents duplicate queue entries on retry. Existing in-app notifications and Firebase password reset emails are independent.

## Enable delivery

1. In Firebase project `facefit-ai-capstone`, install [Trigger Email](https://firebase.google.com/docs/extensions/official/firestore-send-email) with `mail` as the collection. Configure an authorized SMTP sender and keep the credentials in the extension's secret configuration, outside this repository. Blaze enables the extension but does not provide SMTP credentials.
2. Confirm the deployed Firestore rules deny client reads and writes to `mail`. Only the Cloud Function and extension should access it. Do not redeploy unrelated Firestore or Hosting settings as part of this change.
3. From `web/admin`, deploy only the queue function: `firebase deploy --only functions:queueRetailerDecisionEmail --project facefit-ai-capstone`.
4. Review a disposable pending retailer whose Firebase Auth email is a test inbox you control. Check the `mail` document's delivery status and the inbox. Existing decisions are not replayed.
