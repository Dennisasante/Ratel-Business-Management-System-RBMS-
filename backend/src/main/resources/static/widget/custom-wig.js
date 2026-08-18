/*
 * Ratel custom wig configurator — copy-paste embed for a client's
 * WordPress/WooCommerce site. Drop this next to a container element:
 *
 *   <div id="ratel-custom-wig"></div>
 *   <script src="https://YOUR-RATEL-BACKEND/widget/custom-wig.js"
 *           data-business-id="00000000-0000-0000-0000-000000000000"
 *           data-api-base="https://YOUR-RATEL-BACKEND"
 *           data-target="ratel-custom-wig"
 *           data-accent-color="#a76545"
 *           async></script>
 *
 * Same technical approach as booking.js: vanilla JS, no dependencies, runs
 * inside a Shadow DOM so nothing here can be broken by (or leak into) the
 * host theme's CSS.
 */
(function () {
  "use strict";

  var currentScript = document.currentScript;
  if (!currentScript) return;

  var businessId = currentScript.getAttribute("data-business-id");
  var apiBase = (currentScript.getAttribute("data-api-base") || "").replace(/\/$/, "");
  var targetId = currentScript.getAttribute("data-target");
  var accentColor = currentScript.getAttribute("data-accent-color") || "#a76545";

  if (!businessId || !apiBase) {
    console.error("[Ratel custom wig widget] data-business-id and data-api-base are required.");
    return;
  }

  var host = targetId ? document.getElementById(targetId) : null;
  if (!host) {
    host = document.createElement("div");
    currentScript.parentNode.insertBefore(host, currentScript.nextSibling);
  }

  var root = host.attachShadow ? host.attachShadow({ mode: "open" }) : host;

  var ICONS = {
    sparkle:
      '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M12 2l1.8 5.6L19 9l-5.2 1.4L12 16l-1.8-5.6L5 9l5.2-1.4z"/></svg>',
    user:
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-4 5-6 8-6s6.5 2 8 6"/></svg>',
    mail:
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/></svg>',
    whatsapp:
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12.04 2c-5.5 0-10 4.5-10 10 0 1.8.47 3.45 1.29 4.9L2 22l5.25-1.38a9.9 9.9 0 0 0 4.79 1.22h.01c5.5 0 10-4.5 10-10s-4.5-10-10-10zm5.86 14.2c-.25.7-1.45 1.34-2 1.42-.51.08-1.15.11-1.86-.12-.43-.14-.98-.32-1.68-.63-2.96-1.28-4.89-4.26-5.04-4.46-.15-.2-1.2-1.6-1.2-3.05s.76-2.16 1.03-2.46c.27-.3.59-.37.78-.37h.56c.18 0 .42-.03.65.5.25.6.85 2.05.92 2.2s.12.32.02.52c-.35.7-.71.98-.91 1.22-.17.2-.3.36-.12.68.18.32.8 1.32 1.72 2.14 1.18 1.05 2.18 1.38 2.5 1.53.32.15.5.13.7-.08.2-.2.83-.94 1.05-1.27.22-.32.44-.27.73-.16.3.1 1.9.9 2.22 1.06.32.16.53.24.6.37.08.14.08.79-.17 1.49z"/></svg>',
    check:
      '<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
    camera:
      '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>',
  };

  function hexToRgb(hex) {
    var m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return m ? { r: parseInt(m[1], 16), g: parseInt(m[2], 16), b: parseInt(m[3], 16) } : { r: 167, g: 101, b: 69 };
  }
  var accentRgb = hexToRgb(accentColor);
  var accentSoft = "rgba(" + accentRgb.r + "," + accentRgb.g + "," + accentRgb.b + ",0.1)";

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
    ".rbw .rbw-total{display:flex;align-items:center;justify-content:space-between;margin-top:20px;" +
    "padding:13px 15px;border-radius:14px;background:" + accentSoft + ";}" +
    ".rbw .rbw-total-label{font-size:13px;font-weight:600;color:#4a3d2f;}" +
    ".rbw .rbw-total-amount{font-size:19px;font-weight:700;color:" + accentColor + ";}" +
    ".rbw .rbw-photo-row{display:flex;align-items:center;gap:10px;}" +
    ".rbw .rbw-photo-preview{width:52px;height:52px;border-radius:10px;object-fit:cover;border:1.5px solid #ece1d2;}" +
    ".rbw .rbw-photo-btn{display:inline-flex;align-items:center;gap:6px;padding:9px 14px;border-radius:99px;" +
    "border:1.5px solid " + accentColor + ";color:" + accentColor + ";font-size:13px;font-weight:600;cursor:pointer;background:#fff;}" +
    ".rbw button{margin-top:22px;width:100%;padding:13px 16px;border:none;border-radius:999px;" +
    "background:" + accentColor + ";color:#fff;font-size:15px;font-weight:600;cursor:pointer;transition:opacity .15s;" +
    "display:flex;align-items:center;justify-content:center;gap:8px;}" +
    ".rbw button:hover:not(:disabled){opacity:0.92;}" +
    ".rbw button:disabled{opacity:0.55;cursor:not-allowed;}" +
    ".rbw .rbw-back{background:none;border:none;color:#9a8c7c;font-size:13px;font-weight:500;cursor:pointer;" +
    "padding:0;margin:0 0 14px;box-shadow:none;width:auto;display:flex;align-items:center;gap:4px;}" +
    ".rbw .rbw-error{color:#b1432e;font-size:13px;margin-top:10px;background:#fdf1ee;border-radius:10px;padding:9px 12px;}" +
    ".rbw .rbw-success-icon{width:52px;height:52px;border-radius:50%;background:#e8f3ea;color:#3f8a53;" +
    "display:flex;align-items:center;justify-content:center;margin:0 auto 16px;}" +
    ".rbw .rbw-success{text-align:center;padding:6px 0 4px;}" +
    ".rbw .rbw-success h3{text-align:center;}" +
    ".rbw .rbw-muted{font-size:13px;color:#9a8c7c;margin-top:14px;text-align:center;}" +
    ".rbw .rbw-loading{color:#9a8c7c;padding:20px 0;text-align:center;font-size:14px;}" +
    ".rbw .rbw-whatsapp-link{display:flex;align-items:center;justify-content:center;gap:8px;margin-top:16px;" +
    "width:100%;padding:12px 16px;border:1.5px solid #3f8a53;border-radius:999px;color:#3f8a53;" +
    "font-size:14.5px;font-weight:600;text-decoration:none;}" +
    ".rbw .rbw-whatsapp-link:hover{opacity:0.9;text-decoration:none;}" +
    ".rbw-poweredby{display:flex;align-items:center;justify-content:center;gap:6px;" +
    "margin-top:14px;font-size:11.5px;color:#b3a690;font-family:'Poppins','Segoe UI'," +
    "-apple-system,BlinkMacSystemFont,Helvetica,Arial,sans-serif;}" +
    ".rbw-poweredby img{border-radius:2px;}";

  var state = { step: 1, config: null, selections: {}, photoFile: null };

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

  function formatMoney(amount, currency) {
    var n = Number(amount);
    return (currency || "") + " " + (isNaN(n) ? amount : n.toFixed(2));
  }

  function stepDots(step) {
    var out = "";
    for (var i = 1; i <= 3; i++) {
      out += '<div class="rbw-step-dot ' + (i < step ? "rbw-done" : i === step ? "rbw-active" : "") + '"></div>';
    }
    return '<div class="rbw-steps">' + out + "</div>";
  }

  function header(eyebrow, title) {
    return (
      '<div class="rbw-eyebrow">' + ICONS.sparkle + " " + eyebrow + "</div>" +
      "<h3>" + escapeHtml(title) + "</h3>"
    );
  }

  render('<div class="rbw"><p class="rbw-loading">Loading configurator&hellip;</p></div>');

  fetch(apiBase + "/api/public/custom-wig/config?businessId=" + encodeURIComponent(businessId))
    .then(function (res) { return res.json(); })
    .then(function (config) {
      state.config = config;
      if (!config.enabled || !config.attributes || config.attributes.length === 0) {
        render('<div class="rbw"><p class="rbw-muted" style="margin-top:0;">Custom requests aren\'t available for this business right now.</p></div>');
        return;
      }
      config.attributes.forEach(function (attr) {
        state.selections[attr.id] = attr.options.length ? attr.options[0].id : null;
      });
      renderStepOne();
    })
    .catch(function () {
      render('<div class="rbw"><p class="rbw-error">Couldn\'t load the configurator. Please try again.</p></div>');
    });

  function currentTotal() {
    var total = 0;
    state.config.attributes.forEach(function (attr) {
      var selectedId = state.selections[attr.id];
      var option = attr.options.find(function (o) { return o.id === selectedId; });
      if (option) total += Number(option.priceModifier);
    });
    return total;
  }

  function renderStepOne() {
    var fields = state.config.attributes
      .map(function (attr) {
        var options = attr.options
          .map(function (o) {
            return '<option value="' + o.id + '">' + escapeHtml(o.label) + " (+" + formatMoney(o.priceModifier, state.config.currency) + ")</option>";
          })
          .join("");
        return (
          '<label for="rbw-attr-' + attr.id + '">' + escapeHtml(attr.name) + "</label>" +
          '<select id="rbw-attr-' + attr.id + '" data-attr="' + attr.id + '">' + options + "</select>"
        );
      })
      .join("");

    render(
      '<div class="rbw">' +
        header("Design your own", state.config.businessName) +
        stepDots(1) +
        fields +
        '<div class="rbw-total"><span class="rbw-total-label">Estimated price</span>' +
        '<span class="rbw-total-amount" id="rbw-total">' + formatMoney(currentTotal(), state.config.currency) + "</span></div>" +
        '<button id="rbw-next">Continue</button>' +
        "</div>"
    );

    root.querySelectorAll("select[data-attr]").forEach(function (select) {
      select.addEventListener("change", function () {
        state.selections[select.getAttribute("data-attr")] = select.value;
        qs("#rbw-total").textContent = formatMoney(currentTotal(), state.config.currency);
      });
    });

    qs("#rbw-next").addEventListener("click", function () {
      renderStepTwo();
    });
  }

  function renderStepTwo() {
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
        '<label>' + ICONS.camera + " Inspiration photo (optional)</label>" +
        '<div class="rbw-photo-row">' +
        '<input type="file" accept="image/png,image/jpeg,image/webp" id="rbw-photo-input" style="display:none;" />' +
        '<button type="button" class="rbw-photo-btn" id="rbw-photo-btn">' + ICONS.camera + " Choose photo</button>" +
        '<img id="rbw-photo-preview" class="rbw-photo-preview" style="display:none;" alt="" />' +
        "</div>" +
        '<label for="rbw-notes">Notes (optional)</label>' +
        '<textarea id="rbw-notes"></textarea>' +
        '<div id="rbw-form-error"></div>' +
        '<button type="submit">Submit request</button>' +
        "</form>" +
        "</div>"
    );

    qs("#rbw-back").addEventListener("click", renderStepOne);

    qs("#rbw-photo-btn").addEventListener("click", function () {
      qs("#rbw-photo-input").click();
    });
    qs("#rbw-photo-input").addEventListener("change", function (e) {
      var file = e.target.files && e.target.files[0];
      if (!file) return;
      state.photoFile = file;
      var reader = new FileReader();
      reader.onload = function (ev) {
        var preview = qs("#rbw-photo-preview");
        preview.src = ev.target.result;
        preview.style.display = "block";
      };
      reader.readAsDataURL(file);
    });

    qs("#rbw-form").addEventListener("submit", function (e) {
      e.preventDefault();
      var button = qs("#rbw-form button[type=submit]");
      var errorEl = qs("#rbw-form-error");
      errorEl.innerHTML = "";

      var selections = Object.keys(state.selections).map(function (attrId) {
        return { attributeId: attrId, optionId: state.selections[attrId] };
      });
      var payload = {
        customerName: qs("#rbw-name").value.trim(),
        customerEmail: qs("#rbw-email").value.trim(),
        customerWhatsapp: qs("#rbw-whatsapp").value.trim(),
        selections: selections,
        notes: qs("#rbw-notes").value.trim() || null,
      };

      button.disabled = true;
      button.textContent = "Submitting…";

      var formData = new FormData();
      formData.append("payload", JSON.stringify(payload));
      if (state.photoFile) formData.append("photo", state.photoFile);

      fetch(apiBase + "/api/public/custom-wig/requests?businessId=" + encodeURIComponent(businessId), {
        method: "POST",
        body: formData,
      })
        .then(function (res) {
          return res.json().catch(function () { return {}; }).then(function (body) {
            if (!res.ok) throw new Error(body.error || "Something went wrong. Please try again.");
            return body;
          });
        })
        .then(function (created) {
          renderSuccess(created);
        })
        .catch(function (err) {
          errorEl.innerHTML = '<p class="rbw-error">' + escapeHtml(err.message) + "</p>";
          button.disabled = false;
          button.textContent = "Submit request";
        });
    });
  }

  function renderSuccess(created) {
    var whatsappLink = state.config.businessWhatsappLink
      ? '<a class="rbw-whatsapp-link" href="' + state.config.businessWhatsappLink + '" target="_blank" rel="noopener">' +
        ICONS.whatsapp + " Message us on WhatsApp</a>"
      : "";

    render(
      '<div class="rbw">' +
        stepDots(3) +
        '<div class="rbw-success">' +
        '<div class="rbw-success-icon">' + ICONS.check + "</div>" +
        "<h3>Request #" + created.requestNumber + " received</h3>" +
        "<p>Estimated at " + formatMoney(created.estimatedPrice, state.config.currency) + ". We'll follow up with a final quote soon.</p>" +
        "</div>" +
        whatsappLink +
        "</div>"
    );
  }
})();
