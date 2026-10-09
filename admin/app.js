"use strict";

// Set this to your deployed backend URL when it is available.
// Example: https://your-backend.example.com
const API_BASE = "http://localhost:3000/api";

const $ = (id) => document.getElementById(id);

const loginSection = $("loginSection");
const dashboardSection = $("dashboardSection");
const loginForm = $("loginForm");
const zoneForm = $("zoneForm");
const loginMessage = $("loginMessage");
const zoneMessage = $("zoneMessage");
const zoneList = $("zoneList");
const zoneCount = $("zoneCount");
const logoutBtn = $("logoutBtn");

let authToken = null;
let currentUser = null;
let zones = [];

function showMessage(element, message, type = "") {
  element.textContent = message;
  element.className = `message ${type}`.trim();
}

function setLoggedIn(user, token) {
  currentUser = user;
  authToken = token;

  loginSection.classList.add("hidden");
  dashboardSection.classList.remove("hidden");
  logoutBtn.classList.remove("hidden");

  $("accountInfo").textContent =
    `${user.display_name} · ${user.role}`;

  showMessage(loginMessage, "");
}

function showLoggedOut() {
  currentUser = null;
  authToken = null;
  zones = [];

  loginSection.classList.remove("hidden");
  dashboardSection.classList.add("hidden");
  logoutBtn.classList.add("hidden");

  $("accountInfo").textContent = "";
  zoneCount.textContent = "0";
  zoneList.replaceChildren();

  const message = document.createElement("p");
  message.className = "muted";
  message.textContent = "Sign in to load zones.";
  zoneList.append(message);
}

async function apiRequest(path, options = {}) {
  const headers = {
    Accept: "application/json",
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    ...options.headers
  };

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    cache: "no-store"
  });

  let data = {};
  try {
    data = await response.json();
  } catch {
    data = {};
  }

  if (!response.ok) {
    if (response.status === 401 && authToken) {
      showLoggedOut();
      showMessage(loginMessage, "Your session expired. Please sign in again.", "error");
    }

    throw new Error(data.message || `Request failed (${response.status})`);
  }

  return data;
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  showMessage(loginMessage, "Signing in...");

  const email = $("email").value.trim();
  const password = $("password").value;

  try {
    const data = await apiRequest("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    });

    if (!data.token || !data.user) {
      throw new Error("Unexpected login response.");
    }

    // Keep the token in memory only; it is not written to localStorage.
    setLoggedIn(data.user, data.token);

    await loadZones();
  } catch (error) {
    showMessage(loginMessage, error.message || "Login failed.", "error");
  } finally {
    $("password").value = "";
  }
});

logoutBtn.addEventListener("click", () => {
  showLoggedOut();
  $("password").value = "";
  showMessage(loginMessage, "You have signed out.", "success");
});

$("refreshBtn").addEventListener("click", async () => {
  try {
    await loadZones();
  } catch (error) {
    showMessage(zoneMessage, error.message, "error");
  }
});

async function loadZones() {
  zoneList.replaceChildren();

  const loading = document.createElement("p");
  loading.className = "muted";
  loading.textContent = "Loading zones...";
  zoneList.append(loading);

  const data = await apiRequest("/zones");
  zones = Array.isArray(data.zones) ? data.zones : [];

  renderZones();
  showMessage(zoneMessage, "");
}

function renderZones() {
  zoneList.replaceChildren();
  zoneCount.textContent = String(zones.length);

  if (zones.length === 0) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.textContent = "No active zones found.";
    zoneList.append(empty);
    return;
  }

  for (const zone of zones) {
    const card = document.createElement("article");
    card.className = "zone-card";

    const title = document.createElement("h4");
    title.textContent = zone.name || "Unnamed zone";

    const category = document.createElement("p");
    category.textContent = `Category: ${zone.category || "OTHER"}`;

    const radius = document.createElement("p");
    radius.textContent = `Radius: ${Number(zone.radius_m)} metres`;

    const action = document.createElement("p");
    action.textContent = `Action: ${zone.sound_action || "SILENT"}`;

    const version = document.createElement("p");
    version.textContent = `Version: ${zone.version ?? "—"}`;

    const coords = document.createElement("p");
    coords.textContent =
      `Coordinates: ${zone.latitude}, ${zone.longitude}`;

    const actions = document.createElement("div");
    actions.className = "zone-actions";

    const editButton = document.createElement("button");
    editButton.type = "button";
    editButton.className = "secondary";
    editButton.textContent = "Edit";
    editButton.addEventListener("click", () => fillEditForm(zone));

    const deactivateButton = document.createElement("button");
    deactivateButton.type = "button";
    deactivateButton.className = "secondary";
    deactivateButton.textContent = "Deactivate";
    deactivateButton.addEventListener("click", () => deactivateZone(zone));

    actions.append(editButton, deactivateButton);
    card.append(title, category, radius, action, version, coords, actions);
    zoneList.append(card);
  }
}

function fillEditForm(zone) {
  $("areaId").value = zone.area_id;
  $("zoneName").value = zone.name;
  $("category").value = zone.category;
  $("latitude").value = zone.latitude;
  $("longitude").value = zone.longitude;
  $("radius").value = String(zone.radius_m);
  $("soundAction").value = zone.sound_action;
  $("scheduleEnabled").checked = Boolean(zone.schedule_enabled);
  $("scheduleStart").value = zone.schedule_start || "";
  $("scheduleEnd").value = zone.schedule_end || "";
  $("timezone").value = zone.timezone || "Asia/Kolkata";

  // Use the existing form for edits.
  zoneForm.dataset.editId = zone.id;

  const submitButton = zoneForm.querySelector('button[type="submit"]');
  submitButton.textContent = "Save zone changes";

  zoneForm.scrollIntoView({ behavior: "smooth", block: "start" });
  showMessage(zoneMessage, `Editing: ${zone.name}`, "success");
}

function resetZoneForm() {
  delete zoneForm.dataset.editId;

  const submitButton = zoneForm.querySelector('button[type="submit"]');
  submitButton.textContent = "Create zone";
}

$("scheduleEnabled").addEventListener("change", () => {
  $("scheduleFields").classList.toggle(
    "hidden",
    !$("scheduleEnabled").checked
  );
});

$("locationBtn").addEventListener("click", () => {
  if (!navigator.geolocation) {
    showMessage(zoneMessage, "Location is not supported by this browser.", "error");
    return;
  }

  showMessage(zoneMessage, "Getting your current location...");

  navigator.geolocation.getCurrentPosition(
    (position) => {
      $("latitude").value = position.coords.latitude.toFixed(7);
      $("longitude").value = position.coords.longitude.toFixed(7);
      showMessage(
        zoneMessage,
        "Coordinates filled. Verify they match the facility location.",
        "success"
      );
    },
    (error) => {
      const messages = {
        1: "Location permission denied.",
        2: "Current location is unavailable.",
        3: "Location request timed out."
      };

      showMessage(
        zoneMessage,
        messages[error.code] || "Could not get location.",
        "error"
      );
    },
    { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 }
  );
});

zoneForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (!currentUser || !authToken) {
    showMessage(zoneMessage, "Please sign in first.", "error");
    return;
  }

  const submitButton = zoneForm.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  showMessage(zoneMessage, "Saving zone...");

  const body = {
    area_id: $("areaId").value.trim(),
    name: $("zoneName").value.trim(),
    category: $("category").value,
    latitude: Number($("latitude").value),
    longitude: Number($("longitude").value),
    radius_m: Number($("radius").value),
    sound_action: $("soundAction").value,
    schedule_enabled: $("scheduleEnabled").checked,
    schedule_start: $("scheduleEnabled").checked
      ? $("scheduleStart").value
      : null,
    schedule_end: $("scheduleEnabled").checked
      ? $("scheduleEnd").value
      : null,
    timezone: $("timezone").value.trim()
  };

  try {
    const editId = zoneForm.dataset.editId;

    const data = await apiRequest(
      editId ? `/zones/${encodeURIComponent(editId)}` : "/zones",
      {
        method: editId ? "PATCH" : "POST",
        body: JSON.stringify(body)
      }
    );

    showMessage(
      zoneMessage,
      editId ? "Zone updated." : "Zone created.",
      "success"
    );

    zoneForm.reset();
    $("timezone").value = "Asia/Kolkata";
    $("scheduleFields").classList.add("hidden");
    resetZoneForm();

    await loadZones();
  } catch (error) {
    showMessage(zoneMessage, error.message || "Could not save zone.", "error");
  } finally {
    submitButton.disabled = false;
  }
});

async function deactivateZone(zone) {
  const confirmed = window.confirm(
    `Deactivate "${zone.name}"? Devices will apply this change after syncing.`
  );

  if (!confirmed) return;

  try {
    await apiRequest(`/zones/${encodeURIComponent(zone.id)}`, {
      method: "PATCH",
      body: JSON.stringify({ is_active: false })
    });

    showMessage(zoneMessage, "Zone deactivated.", "success");
    await loadZones();
  } catch (error) {
    showMessage(zoneMessage, error.message || "Could not deactivate zone.", "error");
  }
}

// Start at the login screen. Tokens are not persisted across reloads.
showLoggedOut();
