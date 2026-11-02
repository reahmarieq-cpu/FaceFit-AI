# Store kiosk readiness

This is a proposed implementation order based on the current kiosk source. Full-screen scanning, mirrored capture, portrait/landscape layouts, an End scan action, and keeping the display awake while FaceFit is foreground are implemented. The items below remain planned.

| Priority | Work | Current gap and acceptance criteria |
| --- | --- | --- |
| 1 | Finish the customer journey | Results, frame details, selection confirmation, and session completion currently show a placeholder toast in MainActivity. Implement recommendations from the store catalog, frame selection, VTO, and a clear finish screen. Replace raw analysis metrics in the customer UI with understandable fitting results. |
| 2 | Reset between customers | Add an inactivity warning and automatic return to Welcome; delete the photo, landmarks, measurements, and selection while keeping the shop signed in. Apply cleanup to completion, cancellation, and restart after an interrupted session. An abandoned photo must never appear for the next customer. |
| 3 | Authenticate and protect staff controls | Replace MockShopAuthRepository with approved shop accounts and real session handling. Keep sign-out, maintenance, and configuration behind staff authentication. Expired sessions should show a staff recovery screen without exposing account details. |
| 4 | Check scan quality before capture | Current face analysis runs after the photo is taken. Add live checks for one face, pose, distance, lighting, and blur. Show measured guidance rather than static success indicators. Add a short cancellable countdown once stable, with an accessible manual capture path. |
| 5 | Validate VTO alignment | VirtualTryOnGateway is currently a placeholder. Align frame assets to the landmarks from the exact mirrored, upright capture; test crop, scale, rotation, and head pose on the SM-T736B in both orientations. The current metrics are image ratios, not calibrated millimeter measurements. |
| 6 | Configure dedicated kiosk operation | Add an authenticated maintenance exit and define managed-device provisioning, app launch after reboot, and updates outside store hours. Full-screen UI hides the system bars during scanning; device lockdown still requires Android dedicated-device management. |
| 7 | Integrate store inventory and staff handoff | Show frames available at the signed-in store, current availability and prices, and a selected-frame summary a customer can show staff. Add QR handoff if needed, with clear choices about what is shared. |
| 8 | Recover from operational failures | Exercise denied permissions, camera disconnects, no face/multiple faces, failed analysis, network loss, and process restart. Keep a usable recovery action on every screen. Cache catalog data with a visible freshness state and keep diagnostic logs free of customer photos. |

Suggested first next implementation: session timeout and cleanup, followed by the complete results-to-frame-selection flow.

Before unattended use, run a full customer journey on the mounted tablet in actual store lighting, then test back-to-back customers, rotation, large text, several hours of operation, and recovery after restarting the device.

Android reference: [Lock task mode for dedicated devices](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode).
