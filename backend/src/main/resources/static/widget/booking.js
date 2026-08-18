/*
 * Ratel booking widget — copy-paste embed for a client's WordPress/WooCommerce
 * site. Drop this next to a container element:
 *
 *   <div id="ratel-booking"></div>
 *   <script src="https://YOUR-RATEL-BACKEND/widget/booking.js"
 *           data-business-id="00000000-0000-0000-0000-000000000000"
 *           data-api-base="https://YOUR-RATEL-BACKEND"
 *           data-target="ratel-booking"
 *           data-accent-color="#a76545"
 *           async></script>
 *
 * `data-target` is optional — omit it and the widget inserts its own
 * container right after this <script> tag. Runs inside a Shadow DOM so
 * nothing here can be broken by (or leak into) the host theme's CSS.
 */
(function () {
  "use strict";

  var currentScript = document.currentScript;
  if (!currentScript) return;

  var businessId = currentScript.getAttribute("data-business-id");
  var apiBase = (currentScript.getAttribute("data-api-base") || "").replace(/\/$/, "");
  var targetId = currentScript.getAttribute("data-target");
  var accentColor = currentScript.getAttribute("data-accent-color") || "#a76545";
  var manageBaseUrl = (currentScript.getAttribute("data-manage-base-url") || "").replace(/\/$/, "");

  if (!businessId || !apiBase) {
    console.error("[Ratel booking widget] data-business-id and data-api-base are required.");
    return;
  }

  var host = targetId ? document.getElementById(targetId) : null;
  if (!host) {
    host = document.createElement("div");
    currentScript.parentNode.insertBefore(host, currentScript.nextSibling);
  }

  var root = host.attachShadow ? host.attachShadow({ mode: "open" }) : host;

  var ICONS = {
    calendar:
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="3"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>',
    user:
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-4 5-6 8-6s6.5 2 8 6"/></svg>',
    mail:
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/></svg>',
    whatsapp:
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12.04 2c-5.5 0-10 4.5-10 10 0 1.8.47 3.45 1.29 4.9L2 22l5.25-1.38a9.9 9.9 0 0 0 4.79 1.22h.01c5.5 0 10-4.5 10-10s-4.5-10-10-10zm5.86 14.2c-.25.7-1.45 1.34-2 1.42-.51.08-1.15.11-1.86-.12-.43-.14-.98-.32-1.68-.63-2.96-1.28-4.89-4.26-5.04-4.46-.15-.2-1.2-1.6-1.2-3.05s.76-2.16 1.03-2.46c.27-.3.59-.37.78-.37h.56c.18 0 .42-.03.65.5.25.6.85 2.05.92 2.2s.12.32.02.52c-.35.7-.71.98-.91 1.22-.17.2-.3.36-.12.68.18.32.8 1.32 1.72 2.14 1.18 1.05 2.18 1.38 2.5 1.53.32.15.5.13.7-.08.2-.2.83-.94 1.05-1.27.22-.32.44-.27.73-.16.3.1 1.9.9 2.22 1.06.32.16.53.24.6.37.08.14.08.79-.17 1.49z"/></svg>',
    check:
      '<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
    lock:
      '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>',
    sparkle:
      '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M12 2l1.8 5.6L19 9l-5.2 1.4L12 16l-1.8-5.6L5 9l5.2-1.4z"/></svg>',
    clock:
      '<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/></svg>',
  };

  function hexToRgb(hex) {
    var m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return m ? { r: parseInt(m[1], 16), g: parseInt(m[2], 16), b: parseInt(m[3], 16) } : { r: 167, g: 101, b: 69 };
  }
  var accentRgb = hexToRgb(accentColor);
  var accentSoft = "rgba(" + accentRgb.r + "," + accentRgb.g + "," + accentRgb.b + ",0.1)";
  var accentBorder = "rgba(" + accentRgb.r + "," + accentRgb.g + "," + accentRgb.b + ",0.35)";

  var STYLE = "" +
    ":host{all:initial;}" +
    ".rbw{font-family:'Poppins','Segoe UI',-apple-system,BlinkMacSystemFont,Helvetica,Arial,sans-serif;" +
    "font-size:15px;line-height:1.55;color:#2a2018;max-width:440px;box-sizing:border-box;" +
    "background:#fdfaf6;border:1px solid #f0e6d8;border-radius:20px;padding:30px;" +
    "box-shadow:0 1px 2px rgba(42,32,24,0.04),0 12px 32px -12px rgba(42,32,24,0.18);}" +
    ".rbw *{box-sizing:border-box;}" +
    ".rbw .rbw-eyebrow{display:inline-flex;align-items:center;gap:6px;text-transform:uppercase;" +
    "letter-spacing:0.12em;font-size:11px;font-weight:600;color:" + accentColor + ";margin:0 0 8px;}" +
    ".rbw h3{margin:0 0 4px;font-size:21px;font-weight:600;letter-spacing:-0.01em;color:#1c140d;}" +
    ".rbw p{margin:0 0 4px;color:#7a6d5f;}" +
    ".rbw .rbw-steps{display:flex;gap:6px;margin:18px 0 22px;}" +
    ".rbw .rbw-step-dot{height:4px;flex:1;border-radius:99px;background:#ecdfcd;transition:background .2s;}" +
    ".rbw .rbw-step-dot.rbw-active,.rbw .rbw-step-dot.rbw-done{background:" + accentColor + ";}" +
    ".rbw label{display:flex;align-items:center;gap:6px;font-size:12.5px;font-weight:600;color:#4a3d2f;margin:16px 0 6px;" +
    "text-transform:uppercase;letter-spacing:0.04em;}" +
    ".rbw label svg{opacity:0.6;}" +
    ".rbw select,.rbw input,.rbw textarea{width:100%;padding:11px 13px;border:1.5px solid #ece1d2;" +
    "border-radius:12px;font-size:14.5px;font-family:inherit;color:#2a2018;background:#fff;transition:border-color .15s;}" +
    ".rbw select:focus,.rbw input:focus,.rbw textarea:focus{outline:none;border-color:" + accentColor + ";}" +
    ".rbw textarea{resize:vertical;min-height:64px;}" +
    ".rbw .rbw-services{display:flex;flex-direction:column;gap:8px;}" +
    ".rbw .rbw-service-card{display:flex;align-items:center;justify-content:space-between;gap:10px;" +
    "padding:13px 15px;border:1.5px solid #ece1d2;border-radius:14px;cursor:pointer;background:#fff;transition:all .15s;}" +
    ".rbw .rbw-service-card:hover{border-color:" + accentBorder + ";}" +
    ".rbw .rbw-service-card.rbw-selected{border-color:" + accentColor + ";background:" + accentSoft + ";}" +
    ".rbw .rbw-service-name{font-weight:600;font-size:14.5px;color:#2a2018;}" +
    ".rbw .rbw-service-type{font-size:12.5px;color:#9a8c7c;margin-top:1px;}" +
    ".rbw .rbw-service-price{font-weight:600;font-size:14px;color:" + accentColor + ";white-space:nowrap;}" +
    ".rbw button{margin-top:22px;width:100%;padding:13px 16px;border:none;border-radius:999px;" +
    "background:" + accentColor + ";color:#fff;font-size:15px;font-weight:600;cursor:pointer;transition:transform .1s,opacity .15s;" +
    "display:flex;align-items:center;justify-content:center;gap:8px;}" +
    ".rbw button:hover:not(:disabled){opacity:0.92;}" +
    ".rbw button:disabled{opacity:0.55;cursor:not-allowed;}" +
    ".rbw button.rbw-secondary{background:#fff;color:" + accentColor + ";border:1.5px solid " + accentColor + ";}" +
    ".rbw button.rbw-ghost{background:transparent;color:#9a8c7c;box-shadow:none;margin-top:10px;font-weight:500;font-size:13px;padding:6px;}" +
    ".rbw .rbw-error{color:#b1432e;font-size:13px;margin-top:10px;background:#fdf1ee;border-radius:10px;padding:9px 12px;}" +
    ".rbw .rbw-success-icon{width:52px;height:52px;border-radius:50%;background:#e8f3ea;color:#3f8a53;" +
    "display:flex;align-items:center;justify-content:center;margin:0 auto 16px;}" +
    ".rbw .rbw-pending-icon{width:52px;height:52px;border-radius:50%;background:#fdf1e7;color:" + accentColor + ";" +
    "display:flex;align-items:center;justify-content:center;margin:0 auto 16px;}" +
    ".rbw .rbw-success,.rbw .rbw-pending{text-align:center;padding:6px 0 4px;}" +
    ".rbw .rbw-success h3,.rbw .rbw-pending h3{text-align:center;}" +
    ".rbw .rbw-muted{font-size:13px;color:#9a8c7c;margin-top:14px;text-align:center;}" +
    ".rbw .rbw-policy-note{font-size:12.5px;color:" + accentColor + ";background:" + accentSoft + ";" +
    "border-radius:10px;padding:9px 12px;margin-top:10px;}" +
    ".rbw .rbw-field-hint{font-size:12px;color:#9a8c7c;margin:6px 0 0;}" +
    ".rbw-poweredby{display:flex;align-items:center;justify-content:center;gap:6px;" +
    "margin-top:14px;font-size:11.5px;color:#b3a690;font-family:'Poppins','Segoe UI'," +
    "-apple-system,BlinkMacSystemFont,Helvetica,Arial,sans-serif;}" +
    ".rbw-poweredby img{border-radius:2px;}" +
    ".rbw a{color:" + accentColor + ";font-weight:600;text-decoration:none;}" +
    ".rbw a:hover{text-decoration:underline;}" +
    ".rbw .rbw-loading{color:#9a8c7c;padding:20px 0;text-align:center;font-size:14px;}" +
    ".rbw .rbw-secure{display:flex;align-items:center;justify-content:center;gap:5px;font-size:11.5px;" +
    "color:#b3a690;margin-top:12px;}" +
    ".rbw .rbw-back{background:none;border:none;color:#9a8c7c;font-size:13px;font-weight:500;cursor:pointer;" +
    "padding:0;margin:0 0 14px;box-shadow:none;width:auto;display:flex;align-items:center;gap:4px;}" +
    ".rbw .rbw-whatsapp-link{display:flex;align-items:center;justify-content:center;gap:8px;margin-top:12px;" +
    "width:100%;padding:12px 16px;border:1.5px solid #3f8a53;border-radius:999px;color:#3f8a53;" +
    "font-size:14.5px;font-weight:600;text-decoration:none;}" +
    ".rbw .rbw-whatsapp-link:hover{opacity:0.9;text-decoration:none;}";

  var state = { step: 1, config: null, services: [], selectedKey: null };

  function serviceKey(s) {
    return s.isPackage ? "pkg:" + s.packageId : "svc:" + s.serviceCatalogId;
  }

  function findSelectedService() {
    return state.services.find(function (s) { return serviceKey(s) === state.selectedKey; });
  }

  var POWERED_BY =
    '<div class="rbw-poweredby">' +
    '<img src="' + apiBase + '/branding/tallia-icon-mark.svg" alt="" width="14" height="14" />' +
    "Powered by Ratel Systems</div>";

  function render(html) {
    root.innerHTML = "<style>" + STYLE + "</style>" + html + POWERED_BY;
  }

  function qs(sel) {
    return root.querySelector(sel);
  }

  function escapeHtml(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function api(path, options) {
    return fetch(apiBase + path, Object.assign({ headers: { "Content-Type": "application/json" } }, options))
      .then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (body) {
          if (!res.ok) {
            var message = body && body.error ? body.error : "Something went wrong. Please try again.";
            throw new Error(message);
          }
          return body;
        });
      });
  }

  function formatMoney(amount, currency) {
    var n = Number(amount);
    return (currency || "") + " " + (isNaN(n) ? amount : n.toFixed(2));
  }

  function policyNote(service) {
    var policy = service && service.paymentPolicy;
    if (!service || !policy || policy === "NONE") return "";
    if (policy === "FULL") {
      return '<p class="rbw-policy-note">Full payment (' + formatMoney(service.price, state.config.currency) +
        ") is required to confirm this booking.</p>";
    }
    var deposit = Number(service.price) * state.config.depositPercent / 100;
    return '<p class="rbw-policy-note">A ' + state.config.depositPercent + "% deposit (" +
      formatMoney(deposit, state.config.currency) + ") is required to confirm this booking.</p>";
  }

  var DAY_NAMES = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

  function formatTimeLabel(hhmmss) {
    var parts = String(hhmmss).split(":");
    var h = parseInt(parts[0], 10);
    var m = parts[1];
    var ampm = h >= 12 ? "PM" : "AM";
    var h12 = h % 12 === 0 ? 12 : h % 12;
    return h12 + ":" + m + " " + ampm;
  }

  function workingHoursHint(config) {
    var days = (config.workingDays || [])
      .slice()
      .sort(function (a, b) { return a - b; })
      .map(function (d) { return DAY_NAMES[d]; })
      .join(", ");
    return "Open " + days + " · " + formatTimeLabel(config.workingHoursStart) + " – " + formatTimeLabel(config.workingHoursEnd);
  }

  // Mirrors BookingService's server-side check using the same wall-clock
  // reading of the datetime-local value the customer picked — the server
  // stays the authoritative check regardless, this just avoids a round trip
  // for the common case of picking an obviously-closed time.
  function isWithinWorkingHours(whenLocal) {
    if (!whenLocal || !state.config.workingDays) return true;
    var d = new Date(whenLocal);
    var isoWeekday = d.getDay() === 0 ? 7 : d.getDay();
    if (state.config.workingDays.indexOf(isoWeekday) === -1) return false;
    var minutes = d.getHours() * 60 + d.getMinutes();
    var startParts = String(state.config.workingHoursStart).split(":");
    var endParts = String(state.config.workingHoursEnd).split(":");
    var startMinutes = parseInt(startParts[0], 10) * 60 + parseInt(startParts[1], 10);
    var endMinutes = parseInt(endParts[0], 10) * 60 + parseInt(endParts[1], 10);
    return minutes >= startMinutes && minutes < endMinutes;
  }

  function stepDots(step) {
    var out = "";
    for (var i = 1; i <= 3; i++) {
      out += '<div class="rbw-step-dot ' + (i < step ? "rbw-done" : i === step ? "rbw-active" : "") + '"></div>';
    }
    return '<div class="rbw-steps">' + out + "</div>";
  }

  render('<div class="rbw"><p class="rbw-loading">Loading booking form&hellip;</p></div>');

  api("/api/public/bookings/widget-config?businessId=" + encodeURIComponent(businessId))
    .then(function (config) {
      if (!config.enabled) {
        render(
          '<div class="rbw"><p class="rbw-muted" style="margin-top:0;">Online booking isn\'t available for this business right now.</p></div>'
        );
        return null;
      }
      return api("/api/public/bookings/services?businessId=" + encodeURIComponent(businessId)).then(function (services) {
        return { config: config, services: services };
      });
    })
    .then(function (data) {
      if (!data) return;
      state.config = data.config;
      state.services = data.services || [];
      if (state.services.length === 0) {
        render('<div class="rbw"><p class="rbw-muted" style="margin-top:0;">No services are available for online booking yet.</p></div>');
        return;
      }
      renderStepOne();
    })
    .catch(function (err) {
      render('<div class="rbw"><p class="rbw-error">' + escapeHtml(err.message) + "</p></div>");
    });

  function header(eyebrow, title) {
    return (
      '<div class="rbw-eyebrow">' + ICONS.sparkle + " " + eyebrow + "</div>" +
      "<h3>" + escapeHtml(title) + "</h3>"
    );
  }

  function renderStepOne() {
    var minDateTime = new Date(Date.now() + 30 * 60 * 1000).toISOString().slice(0, 16);

    var cards = state.services
      .map(function (s) {
        var key = serviceKey(s);
        var selected = key === state.selectedKey;
        var badge = s.isPackage
          ? '<span style="display:inline-flex;align-items:center;border-radius:999px;padding:1px 8px;font-size:10px;' +
            "font-weight:600;text-transform:uppercase;letter-spacing:0.04em;margin-left:6px;background:" + accentSoft +
            ";color:" + accentColor + ';">Package</span>'
          : "";
        var description = s.description
          ? '<div class="rbw-service-type">' + escapeHtml(s.description) + "</div>"
          : "";
        var included = (s.includedItems || []).length
          ? '<div class="rbw-service-type">' + s.includedItems.map(function (i) { return "• " + escapeHtml(i); }).join("<br/>") + "</div>"
          : "";
        return (
          '<div class="rbw-service-card' + (selected ? " rbw-selected" : "") + '" data-key="' + key + '">' +
          '<div><div class="rbw-service-name">' + escapeHtml(s.serviceName) + badge + "</div>" +
          (s.serviceTypeName ? '<div class="rbw-service-type">' + escapeHtml(s.serviceTypeName) + "</div>" : "") +
          description + included +
          "</div>" +
          '<div class="rbw-service-price">' + formatMoney(s.price, state.config.currency) + "</div>" +
          "</div>"
        );
      })
      .join("");

    render(
      '<div class="rbw">' +
        header("Book your appointment", state.config.businessName) +
        stepDots(1) +
        '<label>' + ICONS.calendar + " Choose a service</label>" +
        '<div class="rbw-services">' + cards + "</div>" +
        '<div id="rbw-policy-note">' + policyNote(findSelectedService()) + "</div>" +
        '<label for="rbw-when">' + ICONS.calendar + " Date &amp; time</label>" +
        '<input type="datetime-local" id="rbw-when" min="' + minDateTime + '" />' +
        '<p class="rbw-field-hint">' + escapeHtml(workingHoursHint(state.config)) + "</p>" +
        '<div id="rbw-step1-error"></div>' +
        '<button id="rbw-next">Continue</button>' +
        "</div>"
    );

    root.querySelectorAll(".rbw-service-card").forEach(function (card) {
      card.addEventListener("click", function () {
        state.selectedKey = card.getAttribute("data-key");
        root.querySelectorAll(".rbw-service-card").forEach(function (c) { c.classList.remove("rbw-selected"); });
        card.classList.add("rbw-selected");
        qs("#rbw-policy-note").innerHTML = policyNote(findSelectedService());
      });
    });

    qs("#rbw-next").addEventListener("click", function () {
      var errorEl = qs("#rbw-step1-error");
      var when = qs("#rbw-when").value;
      if (!state.selectedKey) {
        errorEl.innerHTML = '<p class="rbw-error">Pick a service to continue.</p>';
        return;
      }
      if (!when) {
        errorEl.innerHTML = '<p class="rbw-error">Choose a date and time.</p>';
        return;
      }
      if (!isWithinWorkingHours(when)) {
        errorEl.innerHTML = '<p class="rbw-error">That falls outside business hours (' + escapeHtml(workingHoursHint(state.config)) + "). Please choose another time.</p>";
        return;
      }
      state.scheduledAt = when;
      renderStepTwo();
    });
  }

  function renderStepTwo() {
    var service = findSelectedService();
    var locationField = service && service.requiresLocation
      ? '<label for="rbw-location">' + ICONS.calendar + " Your location</label>" +
        '<textarea id="rbw-location" required placeholder="Where should we come to?"></textarea>'
      : "";

    render(
      '<div class="rbw">' +
        '<button class="rbw-back" id="rbw-back">&larr; Back</button>' +
        header("Almost there", "Your details") +
        stepDots(2) +
        '<form id="rbw-form">' +
        '<label for="rbw-name">' + ICONS.user + " Your name</label>" +
        '<input type="text" id="rbw-name" required />' +
        '<label for="rbw-email">' + ICONS.mail + " Email</label>" +
        '<input type="email" id="rbw-email" required />' +
        '<label for="rbw-whatsapp">' + ICONS.whatsapp + " WhatsApp number</label>" +
        '<input type="tel" id="rbw-whatsapp" placeholder="+233 55 000 0000" required />' +
        locationField +
        '<label for="rbw-notes">Notes (optional)</label>' +
        '<textarea id="rbw-notes"></textarea>' +
        '<div id="rbw-form-error"></div>' +
        '<button type="submit">Confirm booking</button>' +
        "</form>" +
        "</div>"
    );

    qs("#rbw-back").addEventListener("click", function () {
      renderStepOne();
    });

    qs("#rbw-form").addEventListener("submit", function (e) {
      e.preventDefault();
      var button = qs("#rbw-form button[type=submit]");
      var errorEl = qs("#rbw-form-error");
      errorEl.innerHTML = "";

      var locationEl = qs("#rbw-location");
      var payload = {
        scheduledAt: state.scheduledAt ? new Date(state.scheduledAt).toISOString() : null,
        customerName: qs("#rbw-name").value.trim(),
        customerEmail: qs("#rbw-email").value.trim(),
        customerWhatsapp: qs("#rbw-whatsapp").value.trim(),
        notes: qs("#rbw-notes").value.trim() || null,
        customerLocation: locationEl ? locationEl.value.trim() : null,
      };
      if (service && service.isPackage) {
        payload.packageId = service.packageId;
      } else {
        payload.serviceCatalogId = service ? service.serviceCatalogId : null;
      }

      button.disabled = true;
      button.textContent = "Submitting…";

      api("/api/public/bookings?businessId=" + encodeURIComponent(businessId), {
        method: "POST",
        body: JSON.stringify(payload),
      })
        .then(function (created) {
          renderSuccess(created, service);
        })
        .catch(function (err) {
          errorEl.innerHTML = '<p class="rbw-error">' + escapeHtml(err.message) + "</p>";
          button.disabled = false;
          button.textContent = "Confirm booking";
        });
    });
  }

  function renderSuccess(booking, service) {
    var manageLink = manageBaseUrl
      ? '<p class="rbw-muted"><a href="' + manageBaseUrl + "/booking/manage/" + booking.manageToken +
        '" target="_blank" rel="noopener">Manage this booking &rarr;</a></p>'
      : '<p class="rbw-muted">Check your email for a confirmation with a link to manage this booking.</p>';

    var payButton = "";
    var payInPersonButton = "";
    var statusBlock;

    if (booking.paymentRequired) {
      statusBlock =
        '<div class="rbw-pending">' +
        '<div class="rbw-pending-icon">' + ICONS.clock + "</div>" +
        "<h3>Booking #" + booking.bookingNumber + " received</h3>" +
        "<p>Pay " + formatMoney(booking.amountDue, state.config.currency) + " now to confirm your appointment.</p>" +
        "</div>";
      payButton =
        '<button id="rbw-pay">' + formatMoney(booking.amountDue, state.config.currency) + " &middot; Pay to confirm</button>" +
        '<div class="rbw-secure">' + ICONS.lock + " Secured by Paystack</div>";
      if (state.config.allowPayInPerson) {
        payInPersonButton = '<button id="rbw-pay-in-person" class="rbw-secondary">I&rsquo;ll pay in person instead</button>';
      }
    } else {
      statusBlock =
        '<div class="rbw-success">' +
        '<div class="rbw-success-icon">' + ICONS.check + "</div>" +
        "<h3>Booking #" + booking.bookingNumber + " confirmed</h3>" +
        "<p>" + escapeHtml(booking.message || "We'll be in touch on WhatsApp shortly.") + "</p>" +
        "</div>";
      if (!booking._paidInPerson && state.config.paystackPublicKey && service && Number(service.price) > 0) {
        payButton =
          '<button id="rbw-pay" class="rbw-secondary">' + formatMoney(service.price, state.config.currency) + " &middot; Pay now</button>" +
          '<div class="rbw-secure">' + ICONS.lock + " Secured by Paystack</div>";
      }
    }

    var whatsappLink = state.config.businessWhatsappLink
      ? '<a class="rbw-whatsapp-link" href="' + state.config.businessWhatsappLink + '" target="_blank" rel="noopener">' +
        ICONS.whatsapp + " Message us on WhatsApp</a>"
      : "";

    render(
      '<div class="rbw">' +
        stepDots(3) +
        statusBlock +
        payButton +
        payInPersonButton +
        '<div id="rbw-pay-error"></div>' +
        whatsappLink +
        manageLink +
        "</div>"
    );

    var payEl = qs("#rbw-pay");
    if (payEl) {
      payEl.addEventListener("click", function () {
        startPayment(booking.manageToken, payEl, qs("#rbw-pay-error"));
      });
    }

    var payInPersonEl = qs("#rbw-pay-in-person");
    if (payInPersonEl) {
      payInPersonEl.addEventListener("click", function () {
        var errorEl = qs("#rbw-pay-error");
        errorEl.innerHTML = "";
        payInPersonEl.disabled = true;
        payInPersonEl.textContent = "Saving…";
        api("/api/public/bookings/" + booking.manageToken + "/pay-in-person", { method: "POST" })
          .then(function () {
            renderSuccess(
              Object.assign({}, booking, { paymentRequired: false, _paidInPerson: true, message: "Great — pay when you arrive. We'll see you then!" }),
              service
            );
          })
          .catch(function (err) {
            errorEl.innerHTML = '<p class="rbw-error">' + escapeHtml(err.message) + "</p>";
            payInPersonEl.disabled = false;
            payInPersonEl.textContent = "I’ll pay in person instead";
          });
      });
    }
  }

  function loadPaystackScript() {
    return new Promise(function (resolve, reject) {
      if (window.PaystackPop) { resolve(); return; }
      var existing = document.getElementById("paystack-inline-js");
      if (existing) {
        existing.addEventListener("load", function () { resolve(); });
        existing.addEventListener("error", function () { reject(new Error("Couldn't load Paystack.")); });
        return;
      }
      var script = document.createElement("script");
      script.id = "paystack-inline-js";
      script.src = "https://js.paystack.co/v2/inline.js";
      script.async = true;
      script.onload = function () { resolve(); };
      script.onerror = function () { reject(new Error("Couldn't load Paystack.")); };
      document.body.appendChild(script);
    });
  }

  function startPayment(manageToken, buttonEl, errorEl) {
    errorEl.innerHTML = "";
    buttonEl.disabled = true;
    buttonEl.textContent = "Starting…";

    loadPaystackScript()
      .then(function () {
        return api("/api/public/bookings/" + manageToken + "/pay", { method: "POST" });
      })
      .then(function (result) {
        var popup = new window.PaystackPop();
        popup.resumeTransaction(result.accessCode);
        buttonEl.textContent = "Confirm payment";
        buttonEl.disabled = false;
        buttonEl.onclick = function () {
          verifyPayment(result.reference, buttonEl, errorEl);
        };
      })
      .catch(function (err) {
        errorEl.innerHTML = '<p class="rbw-error">' + escapeHtml(err.message) + "</p>";
        buttonEl.disabled = false;
        buttonEl.textContent = "Try payment again";
      });
  }

  function verifyPayment(reference, buttonEl, errorEl) {
    errorEl.innerHTML = "";
    buttonEl.disabled = true;
    buttonEl.textContent = "Checking…";

    api("/api/public/bookings/verify", {
      method: "POST",
      body: JSON.stringify({ reference: reference }),
    })
      .then(function (result) {
        if (result.success) {
          buttonEl.outerHTML = '<p class="rbw-muted" style="color:#3f8a53;font-weight:600;">' + ICONS.check + " Payment confirmed. Thank you!</p>";
        } else {
          errorEl.innerHTML = '<p class="rbw-error">' + escapeHtml(result.message) + "</p>";
          buttonEl.disabled = false;
          buttonEl.textContent = "Confirm payment";
        }
      })
      .catch(function (err) {
        errorEl.innerHTML = '<p class="rbw-error">' + escapeHtml(err.message) + "</p>";
        buttonEl.disabled = false;
        buttonEl.textContent = "Confirm payment";
      });
  }
})();
