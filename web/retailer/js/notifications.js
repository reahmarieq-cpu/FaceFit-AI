// FaceFit AI retailer notifications — Firestore real-time feed, recipient-only.
import { db } from "../../shared/firebase.js";
import { waitForUser } from "../../shared/roles.js";
import { collection, doc, query, where, onSnapshot, updateDoc, serverTimestamp } from "https://www.gstatic.com/firebasejs/12.18.0/firebase-firestore.js";

export async function initRetailerNotifications() {
  const list = document.getElementById("notificationList");
  const dot = document.getElementById("notificationDot");
  const button = document.getElementById("notificationButton");
  if (!list || !dot || !button || button.dataset.notificationsReady) return;
  button.dataset.notificationsReady = "true";
  const user = await waitForUser();
  let items = [];
  const render = () => {
    const unread = items.filter(item => !item.readAt).length;
    dot.classList.toggle("d-none", unread === 0);
    button.setAttribute("aria-label", `Notifications (${unread} unread)`);
    list.replaceChildren();
    const heading = document.createElement("li");
    heading.className = "notification-heading";
    const label = document.createElement("span");
    label.textContent = `Notifications${unread ? ` (${unread} unread)` : ""}`;
    heading.append(label);
    const markAll = document.createElement("button");
    markAll.type = "button";
    markAll.className = "btn btn-link btn-sm p-0";
    markAll.textContent = "Mark all read";
    markAll.disabled = unread === 0;
    markAll.addEventListener("click", async event => {
      event.stopPropagation();
      markAll.disabled = true;
      try {
        await Promise.all(items.filter(item => !item.readAt).map(item =>
          updateDoc(doc(db, "notifications", item.id), { readAt: serverTimestamp() })
        ));
      } catch (error) { console.error("Unable to mark notifications read", error); markAll.disabled = false; }
    });
    heading.append(markAll);
    list.append(heading);
    if (!items.length) {
      const empty = document.createElement("li");
      empty.className = "notification-empty";
      empty.textContent = "No notifications yet";
      list.append(empty);
      return;
    }
    items.slice(0, 30).forEach(item => {
      const li = document.createElement("li");
      const action = document.createElement("button");
      action.type = "button";
      action.className = `dropdown-item notification-entry${item.readAt ? "" : " notification-entry--unread"}`;
      const title = document.createElement("strong");
      title.textContent = item.title || "FaceFit AI update";
      const message = document.createElement("span");
      message.textContent = item.message || "";
      const when = document.createElement("small");
      when.textContent = item.createdAt?.toDate?.().toLocaleString() || "Just now";
      action.append(title, message, when);
      action.addEventListener("click", async () => {
        if (!item.readAt) {
          try { await updateDoc(doc(db, "notifications", item.id), { readAt: serverTimestamp() }); }
          catch (error) { console.error("Unable to mark notification read", error); }
        }
        if (item.type?.startsWith("subscription_")) location.href = "subscription.html";
      });
      li.append(action);
      list.append(li);
    });
  };
  list.textContent = "Loading notifications…";
  const q = query(collection(db, "notifications"), where("userId", "==", user.uid));
  const unsubscribe = onSnapshot(q, snapshot => {
    items = snapshot.docs.map(d => ({ id: d.id, ...d.data() }))
      .sort((a,b) => (b.createdAt?.toMillis?.() || 0) - (a.createdAt?.toMillis?.() || 0));
    render();
  }, error => {
    console.error("Notification feed failed", error);
    list.textContent = "Unable to load notifications. Check Firestore rules.";
    dot.classList.add("d-none");
  });
  window.addEventListener("pagehide", unsubscribe, { once: true });
}
