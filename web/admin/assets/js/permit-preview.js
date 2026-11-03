export function renderPermit(value, host) {
  host.replaceChildren();
  const raw = String(value || "").trim();
  const label = document.createElement("p"); label.className = "text-muted-ff mb-2";
  let url;
  try { const parsed = new URL(raw); if (["https:","http:"].includes(parsed.protocol)) url = parsed.href; } catch {}
  if (!url) { label.textContent = raw ? `Test permit / reference: ${raw} — no image available` : "No permit image provided"; host.append(label); return; }
  const img = document.createElement("img"); img.className = "permit-photo"; img.alt = "Retailer business permit"; img.loading = "lazy"; img.referrerPolicy = "no-referrer";
  img.onerror = () => { img.remove(); label.textContent = "Permit image could not be loaded. Check the image URL."; };
  label.textContent = "Submitted permit image";
  const link = document.createElement("a"); link.href = url; link.target = "_blank"; link.rel = "noopener noreferrer"; link.textContent = "Open original image"; link.className = "d-block mt-2";
  host.append(label,img,link); img.src = url;
}
