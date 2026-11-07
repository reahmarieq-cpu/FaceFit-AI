import { auth } from "./firebase-config.js";

export async function renderPermit(value, host) {
  if (String(value||"").startsWith("firebase://business-permits/")) {
    host.textContent = "Loading private permit…";
    try {
      const user = auth.currentUser;
      if (!user) throw new Error("Admin sign-in required");
      const response = await fetch(`http://localhost:3000/private-file?ref=${encodeURIComponent(value)}`, {
        headers: {Authorization:`Bearer ${await user.getIdToken()}`}
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || "Permit unavailable");
      value = result.url;
    } catch(e) { host.textContent = `Unable to load permit: ${e.message}`; return; }
  }
  host.replaceChildren();
  const raw = String(value || "").trim();
  const label = document.createElement("p"); label.className = "text-muted-ff mb-2";
  let url;
  try { const parsed = new URL(raw); if (["https:","http:"].includes(parsed.protocol)) url = parsed.href; } catch {}
  if (!url) { label.textContent = raw ? `Test permit / reference: ${raw} — no image available` : "No permit image provided"; host.append(label); return; }
  if (/\.pdf(?:[?#]|$)/i.test(url) || /%2F[^?]*\.pdf/i.test(url)) {
    label.textContent = "Submitted business permit (PDF)";
    const link = document.createElement("a"); link.href = url; link.target = "_blank"; link.rel = "noopener noreferrer";
    link.textContent = "View submitted permit PDF"; host.append(label, link); return;
  }
  const img = document.createElement("img"); img.className = "permit-photo"; img.alt = "Retailer business permit"; img.loading = "lazy"; img.referrerPolicy = "no-referrer";
  img.onerror = () => { img.remove(); label.textContent = "Permit image could not be loaded. Check the image URL."; };
  label.textContent = "Submitted permit image";
  const link = document.createElement("a"); link.href = url; link.target = "_blank"; link.rel = "noopener noreferrer"; link.textContent = "Open original image"; link.className = "d-block mt-2";
  host.append(label,img,link); img.src = url;
}
