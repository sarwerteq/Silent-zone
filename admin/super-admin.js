"use strict";

(function () {
  const $ = (id) => document.getElementById(id);
  let adminToolsReady = false;
  let areas = [];
  let admins = [];

  function makeElement(tag, attributes = {}, text = "") {
    const element = document.createElement(tag);

    for (const [key, value] of Object.entries(attributes)) {
      if (key === "className") {
        element.className = value;
      } else {
        element.setAttribute(key, value);
      }
    }

    if (text) element.textContent = text;
    return element;
  }

  function message(element, text, isError = false) {
    element.textContent = text;
    element.className = isError ? "message error" : "message success";
  }

  function field(form, labelText, name, type = "text", required = true) {
    const wrapper = makeElement("div");
    const label = makeElement("label", { for: name }, labelText);
    const input = makeElement("input", {
      id: name,
      name,
      type,
      required: String(required)
    });

    if (type === "password") {
      input.autocomplete = "new-password";
      input.minLength = 10;
      input.maxLength = 72;
    }

    wrapper.append(label, input);
    form.append(wrapper);
    return input;
  }

  function selectField(form, labelText, id, options) {
    const wrapper = makeElement("div");
    const label = makeElement("label", { for: id }, labelText);
    const select = makeElement("select", { id, required: "true" });

    for (const item of options) {
      const option = makeElement("option", { value: item.value }, item.label);
      select.append(option);
    }

    wrapper.append(label, select);
    form.append(wrapper);
    return select;
  }

  function button(text) {
    return makeElement("button", {
      type: "submit",
      className: "primary full"
    }, text);
  }

  async function loadAreas() {
    const data = await apiRequest("/admin/areas");
    areas = Array.isArray(data.areas) ? data.areas : [];
    updateAreaOptions();
  }

  function updateAreaOptions() {
    const select = $("assignmentArea");
    if (!select) return;

    select.replaceChildren();

    if (!areas.length) {
      select.append(makeElement("option", { value: "" }, "Create an area first"));
      return;
    }

    for (const area of areas) {
      select.append(makeElement(
        "option",
        { value: area.id },
        `${area.name} — ${area.district}, ${area.state}`
      ));
    }
  }

  function updateAdminOptions() {
    const select = $("assignmentAdmin");
    if (!select) return;

    select.replaceChildren();

    if (!admins.length) {
      select.append(makeElement("option", { value: "" }, "Create an admin first"));
      return;
    }

    for (const admin of admins) {
      select.append(makeElement(
        "option",
        { value: admin.id },
        `${admin.display_name} (${admin.role}) — ${admin.email}`
      ));
    }
  }

  function createPanel() {
    const panel = makeElement("section", { className: "panel" });
    panel.id = "superAdminPanel";

    panel.append(
      makeElement("h3", {}, "Super Admin Tools"),
      makeElement(
        "p",
        { className: "muted" },
        "Create areas, create admin accounts and assign responsibility."
      )
    );

    // Create area form.
    const areaForm = makeElement("form");
    areaForm.id = "createAreaForm";
    areaForm.append(makeElement("h3", {}, "Create area"));

    field(areaForm, "Area name", "newAreaName");
    field(areaForm, "District", "newAreaDistrict");
    field(areaForm, "State", "newAreaState");

    const country = field(areaForm, "Country", "newAreaCountry");
    country.value = "India";

    areaForm.append(button("Create area"));
    areaForm.append(makeElement("p", {
      id: "areaMessage",
      className: "message",
      role: "status"
    }));

    // Create admin form.
    const adminForm = makeElement("form");
    adminForm.id = "createAdminForm";
    adminForm.append(makeElement("h3", {}, "Create area/district admin"));

    field(adminForm, "Admin display name", "newAdminName");
    field(adminForm, "Admin email", "newAdminEmail", "email");
    field(adminForm, "Temporary password (10–72 characters)", "newAdminPassword", "password");

    selectField(adminForm, "Role", "newAdminRole", [
      { value: "AREA_ADMIN", label: "Area Admin" },
      { value: "DISTRICT_ADMIN", label: "District Admin" }
    ]);

    adminForm.append(button("Create admin account"));
    adminForm.append(makeElement("p", {
      id: "adminMessage",
      className: "message",
      role: "status"
    }));

    // Assign area form.
    const assignmentForm = makeElement("form");
    assignmentForm.id = "assignAreaForm";
    assignmentForm.append(makeElement("h3", {}, "Assign admin to area"));

    selectField(assignmentForm, "Admin account", "assignmentAdmin", [
      { value: "", label: "Create an admin first" }
    ]);

    selectField(assignmentForm, "Area", "assignmentArea", [
      { value: "", label: "Create an area first" }
    ]);

    assignmentForm.append(button("Assign area"));
    assignmentForm.append(makeElement("p", {
      id: "assignmentMessage",
      className: "message",
      role: "status"
    }));

    const areaList = makeElement("div");
    areaList.id = "superAdminAreaList";

    panel.append(areaForm, adminForm, assignmentForm);
    panel.append(makeElement("h3", {}, "Configured areas"), areaList);

    const dashboard = $("dashboardSection");
    const welcome = dashboard.querySelector(".welcome");

    if (welcome && welcome.nextSibling) {
      dashboard.insertBefore(panel, welcome.nextSibling);
    } else {
      dashboard.prepend(panel);
    }

    areaForm.addEventListener("submit", async (event) => {
      event.preventDefault();

      const submit = areaForm.querySelector('button[type="submit"]');
      submit.disabled = true;

      try {
        const data = await apiRequest("/admin/areas", {
          method: "POST",
          body: JSON.stringify({
            name: $("newAreaName").value.trim(),
            district: $("newAreaDistrict").value.trim(),
            state: $("newAreaState").value.trim(),
            country: $("newAreaCountry").value.trim()
          })
        });

        areaForm.reset();
        $("newAreaCountry").value = "India";

        message($("areaMessage"), "Area created successfully.");
        await loadAreas();
        renderAreaList();

        // If the area ID is needed for testing, it is available in the response.
        console.info("Created area ID:", data.area?.id || "(not returned)");
      } catch (error) {
        message($("areaMessage"), error.message, true);
      } finally {
        submit.disabled = false;
      }
    });

    adminForm.addEventListener("submit", async (event) => {
      event.preventDefault();

      const submit = adminForm.querySelector('button[type="submit"]');
      submit.disabled = true;

      try {
        const data = await apiRequest("/admin/admins", {
          method: "POST",
          body: JSON.stringify({
            display_name: $("newAdminName").value.trim(),
            email: $("newAdminEmail").value.trim(),
            password: $("newAdminPassword").value,
            role: $("newAdminRole").value
          })
        });

        if (data.admin) {
          admins.push(data.admin);
          updateAdminOptions();
        }

        adminForm.reset();
        message(
          $("adminMessage"),
          "Admin account created. Assign an area before zone management."
        );
      } catch (error) {
        message($("adminMessage"), error.message, true);
      } finally {
        $("newAdminPassword").value = "";
        submit.disabled = false;
      }
    });

    assignmentForm.addEventListener("submit", async (event) => {
      event.preventDefault();

      const adminId = $("assignmentAdmin").value;
      const areaId = $("assignmentArea").value;

      if (!adminId || !areaId) {
        message($("assignmentMessage"), "Create an admin and area first.", true);
        return;
      }

      const submit = assignmentForm.querySelector('button[type="submit"]');
      submit.disabled = true;

      try {
        await apiRequest("/admin/assignments", {
          method: "POST",
          body: JSON.stringify({
            admin_user_id: adminId,
            area_id: areaId
          })
        });

        message($("assignmentMessage"), "Area assigned successfully.");
      } catch (error) {
        message($("assignmentMessage"), error.message, true);
      } finally {
        submit.disabled = false;
      }
    });
  }

  function renderAreaList() {
    const list = $("superAdminAreaList");
    if (!list) return;

    list.replaceChildren();

    if (!areas.length) {
      list.append(makeElement(
        "p",
        { className: "muted" },
        "No areas created yet."
      ));
      return;
    }

    for (const area of areas) {
      const item = makeElement("article", { className: "zone-card" });
      item.append(
        makeElement("h4", {}, area.name),
        makeElement("p", {}, `${area.district}, ${area.state}, ${area.country}`),
        makeElement("p", {}, `Area ID: ${area.id}`),
        makeElement("p", {}, `Status: ${area.is_active ? "Active" : "Inactive"}`)
      );

      const copy = makeElement("button", {
        type: "button",
        className: "secondary"
      }, "Copy area ID");

      copy.addEventListener("click", async () => {
        try {
          await navigator.clipboard.writeText(area.id);
          copy.textContent = "Copied";
        } catch {
          window.prompt("Copy this area ID:", area.id);
        }
      });

      item.append(copy);
      list.append(item);
    }
  }

  async function showToolsIfNeeded() {
    const dashboard = $("dashboardSection");
    const panel = $("superAdminPanel");

    if (!dashboard || dashboard.classList.contains("hidden")) {
      if (panel) panel.classList.add("hidden");
      return;
    }

    if (!currentUser || currentUser.role !== "SUPER_ADMIN") {
      if (panel) panel.classList.add("hidden");
      return;
    }

    if (!adminToolsReady) {
      createPanel();
      adminToolsReady = true;
    }

    $("superAdminPanel").classList.remove("hidden");

    try {
      await loadAreas();
      renderAreaList();
    } catch (error) {
      const list = $("superAdminAreaList");
      list.replaceChildren(
        makeElement("p", { className: "message error" }, error.message)
      );
    }
  }

  const observer = new MutationObserver(() => {
    showToolsIfNeeded();
  });

  observer.observe($("dashboardSection"), {
    attributes: true,
    attributeFilter: ["class"]
  });

  // Also run after login if the dashboard was already visible.
  showToolsIfNeeded();
})();
