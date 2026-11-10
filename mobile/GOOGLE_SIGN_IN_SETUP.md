# Google sign-in setup for FaceFit mobile

The app uses Android Credential Manager to show Google's account chooser and Firebase Auth to verify the Google ID token. No Google password is collected by FaceFit.

## Firebase setup

1. Open the same Firebase project used by mobile/app/google-services.json.
2. In Authentication > Sign-in method, enable Google, choose the project support email, and save.
3. In Project settings > General > Your apps, select Android package com.example.facefit.
4. Add this computer's debug signing SHA-1:

   25:DC:10:8C:58:E8:07:98:D9:09:D9:B5:7E:CC:F6:A2:FA:CA:D8:1C

   SHA-256 (also useful for the Android app registration):

   81:85:D6:7F:AA:1D:D4:E7:6E:35:57:2F:92:35:5F:01:52:C7:AC:04:A6:D4:D0:42:94:0B:FA:66:0C:BD:29:2F

5. Download a fresh google-services.json and replace D:/AndroidStudioProjects/FaceFit-AI/mobile/app/google-services.json.
6. Sync Gradle, rebuild, and run the app. The Google services plugin generates default_web_client_id from the Web OAuth client in that file. Do not substitute an Android client ID or add a fake client ID.

Other developers must register their own debug-key fingerprints. Release and Play App Signing builds need their respective signing fingerprints as well.

## Test on a phone

- Open Sign in > Continue with Google and select a Google account.
- Confirm the Dashboard opens and Profile shows that account's name and email.
- Cancel the account chooser and confirm the screen remains usable.
- Sign out, then sign in with another Google account. Confirm the account can be changed.
- Verify the signed-in user in Firebase Console > Authentication > Users.
- Confirm email/password login still works.

First Google sign-in authenticates with Firebase and attempts to create users/{uid} only if no document already exists. Existing profile documents are not overwritten. If Firestore permissions or connectivity prevent profile creation, basic profile display falls back to the authenticated Google profile. It does not change Firestore rules; stored profile/scan writes still require appropriate server rules. The checked-in web rules deny user writes, but the deployed rules have not been inspected here.

A provider collision is handled by asking the person to use their original sign-in method. The app does not merge accounts based only on a matching email address.

The Google OAuth config must remain in the app module so the generated client-ID resource is available at build time. A successful build does not establish that Google sign-in is enabled in Firebase or that a real account has completed login.

## Files changed

- app/google-services.json: supplied Google OAuth-enabled config; validated project/app/fingerprint.
- app/build.gradle.kts: Credential Manager and Google ID libraries.
- app/src/main/java/com/example/facefit/data/auth/GoogleSignInClient.kt: account chooser, Google ID token parsing, generated Web OAuth client ID and clearing provider sign-in state.
- app/src/main/java/com/example/facefit/data/auth/AuthManager.kt: Firebase Google token exchange, new-profile initialization, fallback basic profile and sign-out.
- app/src/main/java/com/example/facefit/ui/auth/SignInScreen.kt: real Google button action, loading/cancellation/error handling and existing success navigation.
- app/src/main/java/com/example/facefit/ui/profile/ProfileScreen.kt: asynchronous sign-out and credential-provider state cleanup.
- GOOGLE_SIGN_IN_SETUP.md: these setup and verification instructions.

## Official references

- https://firebase.google.com/docs/auth/android/google-signin
- https://developer.android.com/identity/sign-in/credential-manager-siwg
- https://developers.google.com/identity/android-credential-manager/android/reference/kotlin/com/google/android/libraries/identity/googleid/GetSignInWithGoogleOption

Configuration update: the supplied JSON was checked against the existing Firebase project, Android registration and this computer's debug SHA-1, then copied to app/google-services.json. Real account sign-in still needs phone verification.

Build verification: debug APK built, 49 existing unit tests passed, Android device tests compiled. No live Google account login was run on the phone.


## Google registration

Register also has Continue with Google below the email form. The terms checkbox must be checked. Google registration uses the same verified token flow as Google login; no form name, email or password is required for this option. A first-time Google user creates a Firebase identity; a returning Google user signs into the same identity rather than creating a duplicate.

The account's Google display name and email feed the existing firstName/lastName/email profile fields, dashboard greeting and profile initials. Existing stored profile edits are preserved. The existing profile-storage fallback still applies if Firestore rejects writes. No Google profile-photo UI has been added.

Additional files for registration:
- ui/auth/GoogleAuthButton.kt: shared account chooser, loading, cancellation and error handling for both auth screens.
- ui/auth/RegisterScreen.kt: Google option, separator and terms enforcement; routes through the existing registration success callback.
- ui/auth/SignInScreen.kt: uses the same shared Google button, preserving sign-in behavior.

Manual checks: register with a new Google account without filling the password form; confirm name/email/initials in Dashboard/Profile. Cancel and retry; uncheck terms and confirm Google registration is disabled. Sign out and use the same account from Sign in; verify no duplicate Firebase user.
