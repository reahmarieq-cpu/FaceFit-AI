import { db } from "../../shared/firebase.js";
import { waitForUser, resolveRole } from "../../shared/roles.js";
import { doc, getDoc, updateDoc, serverTimestamp } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

const $ = id => document.getElementById(id);
const feedback = $('shopPinFeedback');
const results = $('shopSearchResults');
const DEFAULT_CENTER = [10.3157, 123.8854]; // Cebu City, starting view only
let retailerId = null;
let selected = null;
let marker = null;
let lastSearchAt = 0;
let searchAbort = null;

function notify(message, error = false) {
  feedback.textContent = message;
  feedback.className = `small mt-2 text-${error ? 'danger' : 'success'}`;
}
function selectPin(lat, lng, zoom = null) {
  lat = Number(lat); lng = Number(lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return;
  selected = { lat, lng };
  if (!marker) {
    marker = L.marker([lat, lng], { draggable: true }).addTo(map);
    marker.on('dragend', () => {
      const p = marker.getLatLng();
      selectPin(p.lat, p.lng);
    });
  } else marker.setLatLng([lat, lng]);
  map.setView([lat, lng], zoom ?? Math.max(map.getZoom(), 16));
  $('shopSelectedLocation').textContent = `Selected: ${lat.toFixed(6)}, ${lng.toFixed(6)} — drag pin to adjust`;

}

const map = L.map('shopInteractiveMap', { scrollWheelZoom: false }).setView(DEFAULT_CENTER, 13);
requestAnimationFrame(() => map.invalidateSize());
window.addEventListener('resize', () => map.invalidateSize());
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap contributors'
}).addTo(map);
map.on('click', event => selectPin(event.latlng.lat, event.latlng.lng));

// OpenStreetMap Nominatim public search: submit-only, no autocomplete, rate-limited.
async function searchShopLocation() {
  const query = $('shopLocationSearch').value.trim();
  if (query.length < 3) { notify('Enter at least 3 characters to search.', true); return; }
  const now = Date.now();
  if (now - lastSearchAt < 1200) { notify('Please wait a moment before searching again.', true); return; }
  lastSearchAt = now;
  if (searchAbort) searchAbort.abort();
  searchAbort = new AbortController();
  $('shopSearchButton').disabled = true;
  results.replaceChildren();
  notify('Searching locations...');
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=${encodeURIComponent(query)}`;
    const response = await fetch(url, { signal: searchAbort.signal, headers: { 'Accept': 'application/json' } });
    if (!response.ok) throw Error(`Location search unavailable (${response.status}). Try again later.`);
    const places = await response.json();
    if (!places.length) { notify('No matching locations. Try a nearby landmark, or click directly on the map.', true); return; }
    for (const place of places) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'list-group-item list-group-item-action text-start small';
      button.textContent = place.display_name;
      button.addEventListener('click', () => {
        selectPin(place.lat, place.lon, 17);
        results.replaceChildren();
        notify('Location found. Drag the pin or click the map to select the exact storefront, then save.');
      });
      results.appendChild(button);
    }
    notify('Select a search result, then adjust the pin if needed.');
  } catch (error) {
    if (error.name !== 'AbortError') notify(error.message || 'Location search failed.', true);
  } finally { $('shopSearchButton').disabled = false; }
}
$('shopSearchButton').addEventListener('click', searchShopLocation);
$('shopLocationSearch').addEventListener('keydown', event => {
  if (event.key === 'Enter') { event.preventDefault(); searchShopLocation(); }
});

$('useShopCurrentLocation').addEventListener('click', () => {
  if (!navigator.geolocation) { notify('Your browser does not support location access.', true); return; }
  notify('Requesting location permission...');
  navigator.geolocation.getCurrentPosition(
    position => {
      selectPin(position.coords.latitude, position.coords.longitude, 17);
      notify('Current device location selected. Move the pin to your actual shop before saving.');
    },
    error => notify(`Unable to get device location: ${error.message}. Search or click the map instead.`, true),
    { enableHighAccuracy: true, timeout: 12000, maximumAge: 30000 }
  );
});

(async () => {
  try {
    const user = await waitForUser();
    const role = await resolveRole(user);
    if (role.role !== 'retailer' || role.data.status !== 'approved') {
      notify('Only approved retailers can save a shop location.', true);
      return;
    }
    retailerId = user.uid;
    const snap = await getDoc(doc(db, 'retailers', retailerId));
    const pin = snap.data()?.shopLocation;
    if (pin && Number.isFinite(pin.lat) && Number.isFinite(pin.lng)) {
      selectPin(pin.lat, pin.lng, 17);
      notify('Saved shop location loaded. You can move the pin and save changes.');
    }
    setTimeout(() => map.invalidateSize(), 100);
  } catch (error) {
    console.error('Shop location load failed:', error);
    notify('Unable to load saved location.', true);
  }
})();

$('saveShopPin').addEventListener('click', async () => {
  const button = $('saveShopPin');
  try {
    if (!retailerId) throw Error('Retailer session is not ready.');
    if (!selected) throw Error('Search for a place or click the map to place a pin first.');
    button.disabled = true;
    await updateDoc(doc(db, 'retailers', retailerId), {
      shopLocation: { lat: selected.lat, lng: selected.lng },
      shopLocationUpdatedAt: serverTimestamp()
    });
    notify('Shop location saved successfully!');
  } catch (error) {
    console.error('Shop location save failed:', error);
    notify(error.code === 'permission-denied' ? 'Firestore denied the update. Check your published retailer location rules.' : error.message, true);
  } finally { button.disabled = false; }
});
