// FaceFit AI retailer decision email — browser-based EmailJS for capstone demonstration.
// EmailJS public identifiers are exposed in the browser by design; never put private keys here.
// WARNING: browser-based sending is not a trusted audit/notification backend.
const EMAILJS_SERVICE_ID = "service_yp16vfu";
const EMAILJS_TEMPLATE_ID = "template_ycwkrcr";
const EMAILJS_PUBLIC_KEY = "REPLACE_WITH_EMAILJS_PUBLIC_KEY";

export function isRetailerEmailConfigured() {
  return EMAILJS_PUBLIC_KEY !== "REPLACE_WITH_EMAILJS_PUBLIC_KEY" && EMAILJS_PUBLIC_KEY.trim().length > 0;
}

function emailContent(status) {
  if (status === "approved") return {
    subject: "Your FaceFit AI Retailer Application Has Been Approved",
    message: "We are pleased to inform you that your application to join FaceFit AI has been approved. You may now log in to your retailer account and access the available features."
  };
  if (status === "rejected") return {
    subject: "FaceFit AI Retailer Application Update",
    message: "Thank you for applying to join FaceFit AI. After reviewing your application, we regret to inform you that it has not been approved."
  };
  throw new Error("Invalid retailer decision for email.");
}

export async function sendRetailerDecisionEmail(retailer, status) {
  if (!isRetailerEmailConfigured()) throw new Error("EmailJS Public Key has not been configured");
  if (!retailer?.emailAddress || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(retailer.emailAddress)) {
    throw new Error("Retailer has no valid registered email address");
  }
  const { subject, message } = emailContent(status);
  const name = retailer.retailName || retailer.contactPerson || "Eyewear Retailer";
  const params = {
    to_email: retailer.emailAddress,
    email_subject: subject,
    retailer_name: name,
    email_message: message
  };
  const response = await fetch("https://api.emailjs.com/api/v1.0/email/send", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      service_id: EMAILJS_SERVICE_ID,
      template_id: EMAILJS_TEMPLATE_ID,
      user_id: EMAILJS_PUBLIC_KEY,
      template_params: params
    })
  });
  if (!response.ok) {
    const reason = (await response.text()).slice(0, 250);
    throw new Error(`EmailJS HTTP ${response.status}: ${reason}`);
  }
}
