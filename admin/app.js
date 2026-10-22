"use strict";

// Set this to your deployed backend URL.
const API_BASE = "https://silent-zone.onrender.com/api";

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

/*
 * =========================================================
 * GENERAL
 * =========================================================
 */

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
  message.textContent =
    "Sign in to load zones.";

  zoneList.append(message);
}

async function apiRequest(path, options = {}) {
  const headers = {
    Accept: "application/json",

    ...(options.body
      ? { "Content-Type": "application/json" }
      : {}),

    ...(authToken
      ? {
          Authorization:
            `Bearer ${authToken}`
        }
      : {}),

    ...options.headers
  };

  const response = await fetch(
    `${API_BASE}${path}`,
    {
      ...options,
      headers,
      cache: "no-store"
    }
  );

  let data = {};

  try {
    data = await response.json();
  } catch {
    data = {};
  }

  if (!response.ok) {
    if (
      response.status === 401 &&
      authToken
    ) {
      showLoggedOut();

      showMessage(
        loginMessage,
        "Your session expired. Please sign in again.",
        "error"
      );
    }

    const extraErrors =
      Array.isArray(data.errors)
        ? `: ${data.errors.join(", ")}`
        : "";

    throw new Error(
      (data.message ||
        `Request failed (${response.status})`) +
        extraErrors
    );
  }

  return data;
}

/*
 * =========================================================
 * LOGIN
 * =========================================================
 */

loginForm.addEventListener(
  "submit",
  async (event) => {
    event.preventDefault();

    showMessage(
      loginMessage,
      "Signing in..."
    );

    const email =
      $("email").value.trim();

    const password =
      $("password").value;

    try {
      const data =
        await apiRequest(
          "/auth/login",
          {
            method: "POST",
            body: JSON.stringify({
              email,
              password
            })
          }
        );

      if (
        !data.token ||
        !data.user
      ) {
        throw new Error(
          "Unexpected login response."
        );
      }

      setLoggedIn(
        data.user,
        data.token
      );

      await loadZones();

    } catch (error) {
      showMessage(
        loginMessage,
        error.message ||
          "Login failed.",
        "error"
      );

    } finally {
      $("password").value = "";
    }
  }
);

logoutBtn.addEventListener(
  "click",
  () => {
    showLoggedOut();

    $("password").value = "";

    showMessage(
      loginMessage,
      "You have signed out.",
      "success"
    );
  }
);

$("refreshBtn").addEventListener(
  "click",
  async () => {
    try {
      await loadZones();

    } catch (error) {
      showMessage(
        zoneMessage,
        error.message,
        "error"
      );
    }
  }
);

/*
 * =========================================================
 * LOAD ZONES
 * =========================================================
 */

async function loadZones() {
  zoneList.replaceChildren();

  const loading =
    document.createElement("p");

  loading.className = "muted";
  loading.textContent =
    "Loading zones...";

  zoneList.append(loading);

  const data =
    await apiRequest("/zones");

  zones =
    Array.isArray(data.zones)
      ? data.zones
      : [];

  renderZones();

  showMessage(
    zoneMessage,
    ""
  );
}

/*
 * =========================================================
 * SCHEDULE UI
 * =========================================================
 */

const SCHEDULE_DAYS = [
  [1, "Mon"],
  [2, "Tue"],
  [3, "Wed"],
  [4, "Thu"],
  [5, "Fri"],
  [6, "Sat"],
  [7, "Sun"]
];

function normaliseScheduleDays(days) {
  if (!Array.isArray(days) || days.length === 0) {
    return SCHEDULE_DAYS.map(
      ([value]) => value
    );
  }

  return [...new Set(
    days.map(Number)
  )]
    .filter(
      (day) =>
        Number.isInteger(day) &&
        day >= 1 &&
        day <= 7
    )
    .sort(
      (a, b) => a - b
    );
}

function scheduleDaysText(days) {
  const selected =
    normaliseScheduleDays(days);

  if (selected.length === 7) {
    return "Every day";
  }

  return SCHEDULE_DAYS
    .filter(
      ([value]) =>
        selected.includes(value)
    )
    .map(
      ([, label]) => label
    )
    .join(", ");
}

function normaliseTime(value) {
  if (!value) return "";

  /*
   * PostgreSQL TIME may return:
   * 05:00:00
   *
   * HTML time input needs:
   * 05:00
   */
  return String(value).slice(0, 5);
}

function addScheduleRow(
  schedule = {}
) {
  const scheduleList =
    $("scheduleList");

  const row =
    document.createElement("div");

  row.className =
    "schedule-row";

  const heading =
    document.createElement("h4");

  heading.className =
    "schedule-heading";

  heading.textContent =
    `Schedule ${
      scheduleList.children.length + 1
    }`;

  const nameLabel =
    document.createElement("label");

  nameLabel.textContent =
    "Schedule name";

  const nameInput =
    document.createElement("input");

  nameInput.type = "text";

  nameInput.className =
    "schedule-name";

  nameInput.maxLength = 100;

  nameInput.placeholder =
    "e.g. Fajr, Zuhr, Meeting";

  nameInput.value =
    schedule.name || "";

  nameInput.required = true;

  /*
   * =====================================================
   * ACTIVE DAYS
   * =====================================================
   */

  const daysLabel =
    document.createElement("label");

  daysLabel.textContent =
    "Active days";

  const daysBox =
    document.createElement("div");

  daysBox.className =
    "schedule-days";

  const selectedDays =
    normaliseScheduleDays(
      schedule.days
    );

  for (
    const [dayValue, dayLabel]
    of SCHEDULE_DAYS
  ) {
    const dayOption =
      document.createElement("label");

    dayOption.className =
      "schedule-day-option";

    const checkbox =
      document.createElement("input");

    checkbox.type = "checkbox";

    checkbox.className =
      "schedule-day";

    checkbox.value =
      String(dayValue);

    checkbox.checked =
      selectedDays.includes(
        dayValue
      );

    const text =
      document.createElement("span");

    text.textContent =
      dayLabel;

    dayOption.append(
      checkbox,
      text
    );

    daysBox.append(
      dayOption
    );
  }

  /*
   * =====================================================
   * TIMES
   * =====================================================
   */

  const times =
    document.createElement("div");

  times.className =
    "two-col";

  /*
   * START
   */

  const startBox =
    document.createElement("div");

  const startLabel =
    document.createElement("label");

  startLabel.textContent =
    "Start time";

  const startInput =
    document.createElement("input");

  startInput.type = "time";

  startInput.className =
    "schedule-start";

  startInput.value =
    normaliseTime(
      schedule.start_time
    );

  startInput.required = true;

  startBox.append(
    startLabel,
    startInput
  );

  /*
   * END
   */

  const endBox =
    document.createElement("div");

  const endLabel =
    document.createElement("label");

  endLabel.textContent =
    "End time";

  const endInput =
    document.createElement("input");

  endInput.type = "time";

  endInput.className =
    "schedule-end";

  endInput.value =
    normaliseTime(
      schedule.end_time
    );

  endInput.required = true;

  endBox.append(
    endLabel,
    endInput
  );

  times.append(
    startBox,
    endBox
  );

  /*
   * REMOVE BUTTON
   */

  const removeButton =
    document.createElement("button");

  removeButton.type = "button";

  removeButton.className =
    "secondary full";

  removeButton.textContent =
    "Remove this time";

  removeButton.addEventListener(
    "click",
    () => {
      row.remove();

      renumberSchedules();
    }
  );

  row.append(
    heading,
    nameLabel,
    nameInput,
    daysLabel,
    daysBox,
    times,
    removeButton
  );

  scheduleList.append(row);

  renumberSchedules();
}

function renumberSchedules() {
  const rows =
    $("scheduleList")
      .querySelectorAll(
        ".schedule-row"
      );

  rows.forEach(
    (row, index) => {
      const heading =
        row.querySelector(
          ".schedule-heading"
        );

      if (heading) {
        heading.textContent =
          `Schedule ${index + 1}`;
      }
    }
  );
}

function clearSchedules() {
  $("scheduleList")
    .replaceChildren();
}

function getSchedulesFromForm() {
  const rows =
    $("scheduleList")
      .querySelectorAll(
        ".schedule-row"
      );

  return Array.from(rows).map(
    (row) => ({
      name:
        row.querySelector(
          ".schedule-name"
        ).value.trim(),

      days:
        Array.from(
          row.querySelectorAll(
            ".schedule-day:checked"
          )
        ).map(
          (checkbox) =>
            Number(
              checkbox.value
            )
        ),

      start_time:
        row.querySelector(
          ".schedule-start"
        ).value,

      end_time:
        row.querySelector(
          ".schedule-end"
        ).value,

      is_active: true
    })
  );
}

function showScheduleFields(
  enabled
) {
  $("scheduleFields")
    .classList.toggle(
      "hidden",
      !enabled
    );

  if (
    enabled &&
    $("scheduleList")
      .children.length === 0
  ) {
    addScheduleRow();
  }
}

$("scheduleEnabled")
  .addEventListener(
    "change",
    () => {
      showScheduleFields(
        $("scheduleEnabled")
          .checked
      );
    }
  );

$("addScheduleBtn")
  .addEventListener(
    "click",
    () => {
      addScheduleRow();
    }
  );

/*
 * =========================================================
 * RENDER ZONES
 * =========================================================
 */

function renderZones() {
  zoneList.replaceChildren();

  zoneCount.textContent =
    String(zones.length);

  if (zones.length === 0) {
    const empty =
      document.createElement("p");

    empty.className = "muted";

    empty.textContent =
      "No active zones found.";

    zoneList.append(empty);

    return;
  }

  for (const zone of zones) {
    const card =
      document.createElement(
        "article"
      );

    card.className =
      "zone-card";

    const title =
      document.createElement("h4");

    title.textContent =
      zone.name ||
      "Unnamed zone";

    const category =
      document.createElement("p");

    category.textContent =
      `Category: ${
        zone.category ||
        "OTHER"
      }`;

    const radius =
      document.createElement("p");

    radius.textContent =
      `Radius: ${
        Number(zone.radius_m)
      } metres`;

    const action =
      document.createElement("p");

    action.textContent =
      `Action: ${
        zone.sound_action ||
        "SILENT"
      }`;

    /*
     * Schedule summary.
     */

    const scheduleInfo =
      document.createElement("p");

    if (!zone.schedule_enabled) {
      scheduleInfo.textContent =
        "Schedule: Always active";

    } else if (
      Array.isArray(
        zone.schedules
      ) &&
      zone.schedules.length > 0
    ) {
      const summary =
        zone.schedules
          .filter(
            (item) =>
              item.is_active !== false
          )
          .map(
            (item) =>
              `${item.name} (${scheduleDaysText(item.days)}): ` +
              `${normaliseTime(
                item.start_time
              )}–` +
              `${normaliseTime(
                item.end_time
              )}`
          )
          .join(" · ");

      scheduleInfo.textContent =
        `Schedule: ${summary}`;

    } else if (
      zone.schedule_start &&
      zone.schedule_end
    ) {
      /*
       * Old single schedule.
       */

      scheduleInfo.textContent =
        `Schedule: ` +
        `${normaliseTime(
          zone.schedule_start
        )}–` +
        `${normaliseTime(
          zone.schedule_end
        )}`;

    } else {
      scheduleInfo.textContent =
        "Schedule: Enabled, no time configured";
    }

    const version =
      document.createElement("p");

    version.textContent =
      `Version: ${
        zone.version ?? "—"
      }`;

    const coords =
      document.createElement("p");

    coords.textContent =
      `Coordinates: ` +
      `${zone.latitude}, ` +
      `${zone.longitude}`;

    const actions =
      document.createElement("div");

    actions.className =
      "zone-actions";

    const editButton =
      document.createElement(
        "button"
      );

    editButton.type = "button";

    editButton.className =
      "secondary";

    editButton.textContent =
      "Edit";

    editButton.addEventListener(
      "click",
      () =>
        fillEditForm(zone)
    );

    const deactivateButton =
      document.createElement(
        "button"
      );

    deactivateButton.type =
      "button";

    deactivateButton.className =
      "secondary";

    deactivateButton.textContent =
      "Deactivate";

    deactivateButton
      .addEventListener(
        "click",
        () =>
          deactivateZone(zone)
      );

    actions.append(
      editButton,
      deactivateButton
    );

    card.append(
      title,
      category,
      radius,
      action,
      scheduleInfo,
      version,
      coords,
      actions
    );

    zoneList.append(card);
  }
}

/*
 * =========================================================
 * EDIT ZONE
 * =========================================================
 */

function fillEditForm(zone) {
  $("areaId").value =
    zone.area_id;

  $("zoneName").value =
    zone.name;

  $("category").value =
    zone.category;

  $("latitude").value =
    zone.latitude;

  $("longitude").value =
    zone.longitude;

  $("radius").value =
    String(zone.radius_m);

  $("soundAction").value =
    zone.sound_action;

  $("timezone").value =
    zone.timezone ||
    "Asia/Kolkata";

  const enabled =
    Boolean(
      zone.schedule_enabled
    );

  $("scheduleEnabled").checked =
    enabled;

  clearSchedules();

  if (enabled) {
    /*
     * New multiple schedules.
     */

    if (
      Array.isArray(
        zone.schedules
      ) &&
      zone.schedules.length > 0
    ) {
      for (
        const schedule
        of zone.schedules
      ) {
        if (
          schedule.is_active !==
          false
        ) {
          addScheduleRow(
            schedule
          );
        }
      }

    } else if (
      zone.schedule_start &&
      zone.schedule_end
    ) {
      /*
       * Automatically convert an old
       * single schedule into the new UI.
       *
       * Old schedules have no days,
       * therefore all 7 days are selected.
       */

      addScheduleRow({
        name: "Schedule 1",

        days: [
          1, 2, 3, 4,
          5, 6, 7
        ],

        start_time:
          zone.schedule_start,

        end_time:
          zone.schedule_end,

        is_active: true
      });

    } else {
      addScheduleRow();
    }
  }

  showScheduleFields(
    enabled
  );

  zoneForm.dataset.editId =
    zone.id;

  const submitButton =
    zoneForm.querySelector(
      'button[type="submit"]'
    );

  submitButton.textContent =
    "Save zone changes";

  zoneForm.scrollIntoView({
    behavior: "smooth",
    block: "start"
  });

  showMessage(
    zoneMessage,
    `Editing: ${zone.name}`,
    "success"
  );
}

function resetZoneForm() {
  delete zoneForm.dataset.editId;

  const submitButton =
    zoneForm.querySelector(
      'button[type="submit"]'
    );

  submitButton.textContent =
    "Create zone";

  clearSchedules();

  $("scheduleEnabled").checked =
    false;

  showScheduleFields(false);
}

/*
 * =========================================================
 * LOCATION
 * =========================================================
 */

$("locationBtn")
  .addEventListener(
    "click",
    () => {
      if (
        !navigator.geolocation
      ) {
        showMessage(
          zoneMessage,
          "Location is not supported by this browser.",
          "error"
        );

        return;
      }

      showMessage(
        zoneMessage,
        "Getting your current location..."
      );

      navigator.geolocation
        .getCurrentPosition(
          (position) => {
            $("latitude").value =
              position.coords
                .latitude
                .toFixed(7);

            $("longitude").value =
              position.coords
                .longitude
                .toFixed(7);

            showMessage(
              zoneMessage,
              `Coordinates filled. Accuracy ±${Math.round(
                position.coords
                  .accuracy
              )} metres. Verify they match the facility location.`,
              "success"
            );
          },

          (error) => {
            const messages = {
              1:
                "Location permission denied.",
              2:
                "Current location is unavailable.",
              3:
                "Location request timed out."
            };

            showMessage(
              zoneMessage,
              messages[
                error.code
              ] ||
                "Could not get location.",
              "error"
            );
          },

          {
            enableHighAccuracy:
              true,

            timeout: 15000,

            /*
             * Facility coordinates should
             * not use an old cached fix.
             */

            maximumAge: 0
          }
        );
    }
  );

/*
 * =========================================================
 * CREATE / UPDATE
 * =========================================================
 */

zoneForm.addEventListener(
  "submit",
  async (event) => {
    event.preventDefault();

    if (
      !currentUser ||
      !authToken
    ) {
      showMessage(
        zoneMessage,
        "Please sign in first.",
        "error"
      );

      return;
    }

    const submitButton =
      zoneForm.querySelector(
        'button[type="submit"]'
      );

    submitButton.disabled =
      true;

    showMessage(
      zoneMessage,
      "Saving zone..."
    );

    const editId =
      zoneForm.dataset.editId;

    const scheduleEnabled =
      $("scheduleEnabled")
        .checked;

    const schedules =
      scheduleEnabled
        ? getSchedulesFromForm()
        : [];

    /*
     * Safety validation.
     */
if (
      scheduleEnabled &&
      schedules.length === 0
    ) {
      showMessage(
        zoneMessage,
        "Add at least one schedule.",
        "error"
      );

      submitButton.disabled =
        false;

      return;
}
 if (scheduleEnabled) {
      for (
        let i = 0;
        i < schedules.length;
        i++
      ) {
        const schedule =
          schedules[i];

        if (
          !schedule.name ||
          !Array.isArray(
            schedule.days
          ) ||
          schedule.days.length === 0 ||
          !schedule.start_time ||
          !schedule.end_time
        ) {
          showMessage(
            zoneMessage,
            `Complete Schedule ${i + 1} and select at least one day.`,
            "error"
          );
          submitButton.disabled =
            false;

          return;
        }
      }
    }

    const body = {
      name:
        $("zoneName")
          .value
          .trim(),

      category:
        $("category").value,

      latitude:
        Number(
          $("latitude").value
        ),
      longitude:
        Number(
          $("longitude").value
        ),

      radius_m:
        Number(
          $("radius").value
        ),

      sound_action:
        $("soundAction").value,

      schedule_enabled:
        scheduleEnabled,

      /*
       * New multiple schedules.
       *
       * If schedule is OFF,
       * send empty [].
       */

      schedules,
      timezone:
        $("timezone")
          .value
          .trim()
    };

    if (!editId) {
      body.area_id =
        $("areaId")
          .value
          .trim();
    }

    try {
      await apiRequest(
        editId
          ? `/zones/${
              encodeURIComponent(
                editId
              )
            }`
          : "/zones",
        {
          method:
            editId
              ? "PATCH"
              : "POST",

          body:
            JSON.stringify(
              body
            )
        }
      );

      showMessage(
        zoneMessage,
        editId
          ? "Zone updated."
          : "Zone created.",
        "success"
      );

      zoneForm.reset();
      $("timezone").value =
        "Asia/Kolkata";

      resetZoneForm();

      await loadZones();

    } catch (error) {
      showMessage(
        zoneMessage,
        error.message ||
          "Could not save zone.",
        "error"
      );

    } finally {
      submitButton.disabled =
        false;
    }
  }
);
/*
 * =========================================================
 * DEACTIVATE
 * =========================================================
 */

async function deactivateZone(
  zone
) {
  const confirmed =
    window.confirm(
      `Deactivate "${zone.name}"? Devices will apply this change after syncing.`
    );

  if (!confirmed) return;

  try {
    await apiRequest(
      `/zones/${
        encodeURIComponent(
          zone.id
        )
      }`,
      {
        method: "PATCH",
        body:
          JSON.stringify({
            is_active: false
          })
      }
    );

    showMessage(
      zoneMessage,
      "Zone deactivated.",
      "success"
    );

    await loadZones();

  } catch (error) {
    showMessage(
      zoneMessage,
      error.message ||
        "Could not deactivate zone.",
      "error"
    );
  }
}
/*
 * =========================================================
 * START AT LOGIN SCREEN
 * =========================================================
 */

showLoggedOut();
