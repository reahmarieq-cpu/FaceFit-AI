import { auth } from "../../shared/firebase.js";
import { sendPasswordResetEmail } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-auth.js";
const form=document.getElementById("resetForm"), button=document.getElementById("resetButton"), message=document.getElementById("resetMessage");
form.addEventListener("submit",async event=>{
 event.preventDefault(); message.textContent=""; const email=document.getElementById("resetEmail").value.trim();
 if(!email)return; button.disabled=true;button.innerHTML='<i class="bi bi-hourglass-split"></i> Sending…';
 try {
   // Auth does not expose a safe public retailer-only lookup. The reset action is
   // intentionally scoped to the retailer UI, not a guarantee of account role.
   await sendPasswordResetEmail(auth,email);
   message.className="alert alert-success mt-3";
   message.textContent="If an account is eligible, a password reset link will arrive shortly. Check your inbox and spam folder.";
 } catch(e) {
   console.error("Password reset request failed",e.code||e);
   if(["auth/user-not-found","auth/invalid-credential"].includes(e.code)) {
     message.className="alert alert-success mt-3";
     message.textContent="If an account is eligible, a password reset link will arrive shortly. Check your inbox and spam folder.";
   } else {
     message.className="alert alert-danger mt-3";
     message.textContent=e.code==="auth/too-many-requests"?"Too many requests. Please try again later.":"Unable to send a reset link right now. Please try again.";
   }
 } finally {button.disabled=false;button.innerHTML='<i class="bi bi-send"></i> Send reset link';}
});
