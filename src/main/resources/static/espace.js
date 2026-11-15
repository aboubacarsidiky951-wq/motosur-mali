"use strict";
(function () {
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var ERR = {
    INVALID_CREDENTIALS: "Identifiant ou mot de passe incorrect.",
    LOCKED: "Compte verrouillé après trop d’essais. Réessayez dans 15 minutes.",
    SUSPENDED: "Ce compte est suspendu. Contactez l’administrateur.",
    WRONG_CURRENT_PASSWORD: "Le mot de passe provisoire est incorrect.",
    WEAK_PASSWORD: "Mot de passe refusé : 12 caractères minimum, différent du provisoire.",
    NOT_STOLEN: "Cette moto n’est plus signalée volée.",
    INVALID_LOGIN: "Identifiant invalide (email pour un commissariat, matricule pour un agent).",
    COMMISSARIAT_REQUIRED: "Indiquez le commissariat.",
    ALREADY_EXISTS: "Ce compte existe déjà.",
    ROLE_NOT_ALLOWED: "Vous ne pouvez pas créer ce niveau de compte.",
    NOT_FOUND: "Compte introuvable.",
    INVALID_COMMUNE: "Commune invalide."
  };
  var ROLE = { CENTRAL: "Direction générale", COMMISSARIAT: "Commissariat", AGENT: "Agent de terrain" };
  var COMMUNES = ["Commune I", "Commune II", "Commune III", "Commune IV", "Commune V", "Commune VI"];
  var me = null, recTarget = null, toastT;

  function cookie(n) { var m = document.cookie.match(new RegExp("(?:^|; )" + n + "=([^;]*)")); return m ? decodeURIComponent(m[1]) : ""; }
  function api(method, url, body) {
    var h = { "Accept": "application/json" };
    if (body !== undefined) h["Content-Type"] = "application/json";
    var t = cookie("XSRF-TOKEN"); if (t) h["X-XSRF-TOKEN"] = t;
    return fetch(url, { method: method, headers: h, credentials: "same-origin", body: body === undefined ? undefined : JSON.stringify(body) })
      .then(function (r) {
        return r.text().then(function (txt) {
          var d = null; try { d = txt ? JSON.parse(txt) : null; } catch (e) { d = null; }
          if (!r.ok) { var e = new Error((d && d.error) || "HTTP_" + r.status); e.status = r.status; throw e; }
          return d;
        });
      });
  }
  function msg(e) { return ERR[e.message] || "Une erreur est survenue (" + e.message + ")."; }
  function el(tag, cls, text) { var n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; }
  function toast(t) { var b = $("#toast"); $("span", b).textContent = t; b.classList.add("show"); clearTimeout(toastT); toastT = setTimeout(function () { b.classList.remove("show"); }, 3200); }
  function fmtDate(v) { if (!v) return "—"; var d = new Date(v); return isNaN(d) ? "—" : d.toLocaleDateString("fr-FR", { day: "2-digit", month: "short", year: "numeric" }); }
  function fmtDT(v) { if (!v) return "—"; var d = new Date(v); return isNaN(d) ? "—" : d.toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }); }
  function phone(p) { return p && p.length === 8 ? "+223 " + p.replace(/(\d\d)(?=\d)/g, "$1 ") : (p || ""); }
  function showErr(id, t) { var n = $("#" + id); n.textContent = t || ""; n.hidden = !t; }
  function busy(f, on) { var b = $("button[type=submit]", f); if (b) b.disabled = on; }
  function show(v) { ["view-login", "view-password", "view-app"].forEach(function (x) { $("#" + x).hidden = x !== v; }); $("#userbar").hidden = v === "view-login"; }
  function authLoss(e) { if (e && (e.status === 401 || e.status === 403)) { me = null; show("view-login"); showErr("l-err", "Session expirée. Reconnectez-vous."); } else toast(msg(e)); }

  var ICON = {
    ok: '<path d="M8 12.5l3 3 5-6"/><circle cx="12" cy="12" r="9"/>',
    stolen: '<path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5M12 18v.01"/>',
    unknown: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.6 2.2c-.7.4-1.1.9-1.1 1.8M12 17v.01"/>'
  };
  function status(kind, title, sub) {
    var box = el("div", "status status-" + kind), ic = el("span", "status-ic");
    ic.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + ICON[kind] + "</svg>";
    var d = el("div"); d.appendChild(el("div", "status-t", title)); d.appendChild(el("div", "status-s", sub || "")); box.appendChild(ic); box.appendChild(d); return box;
  }
  function dl(rows) {
    var d = el("dl", "dl");
    rows.forEach(function (r) { var c = el("div"); c.appendChild(el("dt", null, r[0])); c.appendChild(el("dd", r[2] ? "mono" : null, String(r[1]))); d.appendChild(c); });
    return d;
  }

  /* ---------- navigation ---------- */
  function route() {
    if (!me) { show("view-login"); return; }
    if (me.role === "MAINTENANCE") { location.replace("maintenance.html"); return; }
    if (me.mustChangePassword) { ["p-cur", "p-new", "p-new2"].forEach(function (i) { $("#" + i).value = ""; }); showErr("p-err", ""); show("view-password"); $("#p-cur").focus(); return; }
    $("#who").textContent = me.name + (me.commissariat ? " · " + me.commissariat : "");
    $("#role-label").textContent = ROLE[me.role] || me.role;
    $("#app-title").textContent = me.role === "CENTRAL" ? "Direction générale" : "Espace police";
    $("#tab-dash").hidden = me.role !== "CENTRAL";
    $("#rec-com-field").hidden = me.role !== "CENTRAL";
    var canAdmin = me.role === "CENTRAL" || me.role === "COMMISSARIAT";
    $("#tab-comptes").hidden = !canAdmin;
    $("#a-role-field").hidden = me.role !== "CENTRAL";
    $("#a-com-field").hidden = me.role !== "CENTRAL";
    $("#acc-title").textContent = me.role === "CENTRAL" ? "Créer un compte commissariat ou agent" : "Créer un compte agent pour votre commissariat";
    syncRole();
    show("view-app"); selectTab("verif");
  }
  function selectTab(name) {
    $$("[data-tab]").forEach(function (b) { var on = b.getAttribute("data-tab") === name; b.setAttribute("aria-selected", on ? "true" : "false"); b.tabIndex = on ? 0 : -1; });
    $$("[data-panel]").forEach(function (p) { p.hidden = p.getAttribute("data-panel") !== name; });
    closeRec();
    if (name === "alertes") loadAlerts();
    if (name === "dash") loadDash();
    if (name === "comptes") loadAccounts();
  }
  $$("[data-tab]").forEach(function (b) {
    b.addEventListener("click", function () { selectTab(b.getAttribute("data-tab")); });
    b.addEventListener("keydown", function (ev) {
      var tabs = $$("[data-tab]").filter(function (t) { return !t.hidden; }), i = tabs.indexOf(b), n = null;
      if (ev.key === "ArrowRight") n = tabs[(i + 1) % tabs.length];
      if (ev.key === "ArrowLeft") n = tabs[(i - 1 + tabs.length) % tabs.length];
      if (n) { ev.preventDefault(); n.focus(); selectTab(n.getAttribute("data-tab")); }
    });
  });

  /* ---------- connexion ---------- */
  $("#login-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("l-err", ""); var f = ev.target, l = $("#l-login").value.trim(), p = $("#l-pass").value;
    if (!l || !p) { showErr("l-err", "Saisissez l’identifiant et le mot de passe."); return; }
    busy(f, true);
    api("POST", "/api/auth/login", { login: l, password: p }).then(function (m) { me = m; $("#l-pass").value = ""; route(); })
      .catch(function (e) { showErr("l-err", msg(e)); }).then(function () { busy(f, false); });
  });
  $("#logout").addEventListener("click", function () { api("POST", "/api/auth/logout").catch(function () {}).then(function () { me = null; show("view-login"); }); });
  $("#pw-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("p-err", ""); var f = ev.target, n1 = $("#p-new").value;
    if (n1.length < 12) { showErr("p-err", "12 caractères minimum."); return; }
    if (n1 !== $("#p-new2").value) { showErr("p-err", "Les deux mots de passe ne sont pas identiques."); return; }
    busy(f, true);
    api("POST", "/api/auth/password", { current: $("#p-cur").value, next: n1 }).then(function (m) { me = m; toast("Mot de passe enregistré."); route(); })
      .catch(function (e) { showErr("p-err", msg(e)); }).then(function () { busy(f, false); });
  });

  /* ---------- vérification ---------- */
  function recButton(motoId, plate) {
    var b = el("button", "btn btn-primary btn-sm", "Marquer retrouvée"); b.type = "button";
    b.addEventListener("click", function () { openRec(motoId, plate); }); return b;
  }
  function checkNode(d, q) {
    var box = el("div", "result");
    if (!d.found) {
      box.appendChild(status("unknown", "Non enregistrée", "Le numéro " + q.toUpperCase() + " ne figure pas dans le registre."));
      box.appendChild(el("p", "note", "Contrôler les papiers du conducteur et comparer avec les numéros gravés sur le cadre et le moteur."));
      return box;
    }
    var rows = [["Plaque", d.plate, 1], ["Marque et modèle", d.brand + " " + d.model], ["Couleur", d.color], ["Année", d.year], ["N° de châssis", d.chassis, 1], ["N° de moteur", d.engine, 1],
      ["Propriétaire", d.ownerName], ["Téléphone", phone(d.ownerPhone), 1], ["Commune", d.commune], ["Enregistrée le", fmtDate(d.registeredAt) + " · " + d.reference]];
    if (d.status === "STOLEN" && d.theft) {
      box.appendChild(status("stolen", "Signalée volée", "Vol déclaré le " + fmtDate(d.theft.theftDate) + " · " + d.theft.theftCommune + "."));
      box.appendChild(dl(rows));
      box.appendChild(dl([["Alerte", d.theft.reference, 1], ["Lieu du vol", d.theft.place], ["Déclarée le", fmtDT(d.theft.reportedAt)]]));
      if (d.theft.details) box.appendChild(el("p", "note", "Circonstances : " + d.theft.details));
      var c = el("div", "callout"); c.appendChild(el("b", null, "Conduite proposée.")); c.appendChild(document.createTextNode(" Immobiliser la moto, ne pas la restituer au conducteur et contacter le propriétaire au numéro enregistré."));
      box.appendChild(c);
      var a = el("div", "actions"); a.appendChild(recButton(d.motoId, d.plate)); box.appendChild(a);
    } else if (d.status === "RECOVERED") {
      box.appendChild(status("ok", "En règle", "Retrouvée" + (d.theft && d.theft.recoveredCommissariat ? " · remise au " + d.theft.recoveredCommissariat : "") + "."));
      box.appendChild(dl(rows));
    } else {
      box.appendChild(status("ok", "En règle", "Moto enregistrée, aucune déclaration de vol en cours."));
      box.appendChild(dl(rows));
    }
    return box;
  }
  $("#pol-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); var f = ev.target, q = $("#pol-q").value.trim(), out = $("#pol-result"); out.replaceChildren();
    if (q.replace(/[^A-Za-z0-9]/g, "").length < 4) { out.appendChild(el("p", "err", "Saisissez au moins 4 caractères.")); return; }
    busy(f, true);
    api("GET", "/api/police/check?q=" + encodeURIComponent(q)).then(function (d) { out.appendChild(checkNode(d, q)); })
      .catch(authLoss).then(function () { busy(f, false); });
  });

  /* ---------- alertes ---------- */
  var cs = $("#a-commune"); cs.appendChild(new Option("Toutes les communes", "")); COMMUNES.forEach(function (c) { cs.appendChild(new Option(c, c)); });
  var rc = $("#rec-commune"); COMMUNES.forEach(function (c) { rc.appendChild(new Option(c, c)); });
  var aT; function reloadSoon() { clearTimeout(aT); aT = setTimeout(loadAlerts, 250); }
  $("#a-q").addEventListener("input", reloadSoon); $("#a-commune").addEventListener("change", loadAlerts); $("#a-status").addEventListener("change", loadAlerts);
  function loadAlerts() {
    var u = "/api/police/alerts?status=" + encodeURIComponent($("#a-status").value) + "&q=" + encodeURIComponent($("#a-q").value) + "&commune=" + encodeURIComponent($("#a-commune").value);
    api("GET", u).then(function (list) {
      var body = $("#a-body"); body.replaceChildren();
      if (!list.length) { var r = el("tr"), c = el("td", null, "Aucune alerte."); c.colSpan = 6; r.appendChild(c); body.appendChild(r); return; }
      list.forEach(function (a) {
        var tr = el("tr"), c1 = el("td"); c1.appendChild(el("b", "mono", a.reference)); c1.appendChild(el("small", null, fmtDate(a.theftDate)));
        var c2 = el("td"); c2.appendChild(el("b", "mono", a.plate)); c2.appendChild(el("small", null, a.brand + " " + a.model + " · " + a.color));
        var c3 = el("td"); c3.appendChild(document.createTextNode(a.place)); c3.appendChild(el("small", null, a.theftCommune));
        var c4 = el("td"); c4.appendChild(document.createTextNode(a.ownerName)); c4.appendChild(el("small", "mono", phone(a.ownerPhone)));
        var c5 = el("td"), rec = !!a.recoveredAt;
        c5.appendChild(el("span", "pill " + (rec ? "pill-ok" : "pill-bad"), rec ? "Retrouvée" : "Volée"));
        if (rec && a.recoveredCommissariat) c5.appendChild(el("small", null, a.recoveredCommissariat));
        var c6 = el("td"); if (!rec) c6.appendChild(recButton(a.motoId, a.plate));
        [c1, c2, c3, c4, c5, c6].forEach(function (c) { tr.appendChild(c); }); body.appendChild(tr);
      });
    }).catch(authLoss);
  }

  /* ---------- moto retrouvée ---------- */
  function openRec(id, plate) {
    recTarget = id; $("#rec-title").textContent = "Moto retrouvée : " + plate;
    $("#rec-form").hidden = false; $("#rec-done").hidden = true; showErr("rec-err", "");
    $("#rec-com").value = ""; $("#rec-panel").hidden = false; $("#rec-panel").scrollIntoView({ block: "center" });
  }
  function closeRec() { $("#rec-panel").hidden = true; recTarget = null; }
  $("#rec-cancel").addEventListener("click", closeRec);
  $("#rec-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); var f = ev.target; showErr("rec-err", "");
    var com = me.role === "CENTRAL" ? $("#rec-com").value.trim() : (me.commissariat || "");
    if (!com) { showErr("rec-err", "Indiquez le commissariat où se trouve la moto."); return; }
    busy(f, true);
    api("POST", "/api/police/motos/" + recTarget + "/recovered", { commissariat: com, commune: $("#rec-commune").value }).then(function (d) {
      f.hidden = true; var done = $("#rec-done"); done.replaceChildren(); done.hidden = false;
      done.appendChild(status("ok", "Moto marquée retrouvée", d.plate + " · remise au " + d.commissariat + " · réf. " + d.reference));
      done.appendChild(el("p", "note", (d.smsSent ? "SMS envoyé au propriétaire : « " : "Message prévu pour le propriétaire : « ") + d.sms + " »"));
      if (!d.smsSent) done.appendChild(el("p", "note", "Le SMS n’a pas pu partir : contactez le propriétaire par téléphone."));
      var b = el("button", "btn btn-ghost", "Fermer"); b.type = "button"; b.addEventListener("click", function () { closeRec(); loadAlerts(); var r = $("#pol-result"); r.replaceChildren(); }); done.appendChild(b);
    }).catch(function (e) { if (e.status === 401 || e.status === 403) authLoss(e); else showErr("rec-err", msg(e)); }).then(function () { busy(f, false); });
  });


  /* ---------- comptes (Direction : commissariats et agents ; commissariat : ses agents) ---------- */
  var ROLES = { COMMISSARIAT: "Commissariat", AGENT: "Agent" };
  function curRole() { return me.role === "CENTRAL" ? $("#a-role").value : "AGENT"; }
  function syncRole() {
    var agent = curRole() === "AGENT";
    $("#a-login-label").textContent = agent ? "Matricule de l’agent" : "Email du commissariat";
  }
  $("#a-role").addEventListener("change", syncRole);
  function secretBox(title, login, pw) {
    var b = $("#secret"); b.replaceChildren();
    b.appendChild(el("b", null, title + " : " + login));
    b.appendChild(el("p", "note", "Mot de passe provisoire, affiché une seule fois. Transmettez-le de façon sûre : il devra être changé à la première connexion."));
    var p = el("div", "mono", pw); p.style.cssText = "font-size:1.3rem;font-weight:600;letter-spacing:.08em;user-select:all;overflow-wrap:anywhere;margin:6px 0"; b.appendChild(p);
    var row = el("div", "actions");
    var c = el("button", "btn btn-ghost btn-sm", "Copier"); c.type = "button";
    c.addEventListener("click", function () { if (navigator.clipboard) navigator.clipboard.writeText(pw).then(function () { toast("Copié."); }, function () { toast("Sélectionnez le texte pour le copier."); }); });
    var x = el("button", "btn btn-ghost btn-sm", "Masquer"); x.type = "button";
    x.addEventListener("click", function () { b.hidden = true; b.replaceChildren(); });
    row.appendChild(c); row.appendChild(x); b.appendChild(row); b.hidden = false; b.scrollIntoView({ block: "nearest" });
  }
  function loadAccounts() {
    api("GET", "/api/admin/accounts").then(function (list) {
      var body = $("#acc-body"); body.replaceChildren();
      if (!list.length) { emptyRow(body, 5, "Aucun compte pour le moment."); return; }
      list.forEach(function (a) {
        var c1 = el("td"); c1.appendChild(el("b", null, a.name)); c1.appendChild(el("small", "mono", a.login)); if (a.commissariat) c1.appendChild(el("small", null, a.commissariat));
        var c3 = el("td"), locked = a.lockedUntil && new Date(a.lockedUntil) > new Date();
        c3.appendChild(el("span", "pill " + (!a.active ? "pill-bad" : locked ? "pill-warn" : "pill-ok"), !a.active ? "Suspendu" : locked ? "Verrouillé" : "Actif"));
        if (a.mustChangePassword) c3.appendChild(el("small", null, "Mot de passe provisoire"));
        var c5 = el("td"), act = el("div", "actions");
        var t = el("button", "btn btn-ghost btn-sm", a.active ? "Suspendre" : "Réactiver"); t.type = "button";
        t.addEventListener("click", function () { api("POST", "/api/admin/accounts/" + a.id + "/active", { active: !a.active }).then(function () { toast(a.active ? "Compte suspendu." : "Compte réactivé."); loadAccounts(); }).catch(authLoss); });
        var r = el("button", "btn btn-ghost btn-sm", "Nouveau mot de passe"); r.type = "button";
        r.addEventListener("click", function () {
          if (r.dataset.arm !== "1") { r.dataset.arm = "1"; r.textContent = "Confirmer ?"; setTimeout(function () { r.dataset.arm = ""; r.textContent = "Nouveau mot de passe"; }, 4000); return; }
          api("POST", "/api/admin/accounts/" + a.id + "/reset-password").then(function (res) { secretBox("Nouveau mot de passe", a.login, res.temporaryPassword); loadAccounts(); }).catch(authLoss);
        });
        act.appendChild(t); act.appendChild(r); c5.appendChild(act);
        row(body, [c1, ROLES[a.role] || a.role, c3, fmtDT(a.lastLoginAt), c5]);
      });
    }).catch(authLoss);
  }
  $("#acc-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("a-err", ""); var f = ev.target, role = curRole();
    var body = { login: $("#a-login").value.trim(), name: $("#a-name").value.trim(), role: role, commissariat: me.role === "CENTRAL" ? $("#a-com").value.trim() : "" };
    if (!body.login || !body.name) { showErr("a-err", "Renseignez l’identifiant et le nom."); return; }
    if (me.role === "CENTRAL" && !body.commissariat) { showErr("a-err", "Indiquez le commissariat."); return; }
    busy(f, true);
    api("POST", "/api/admin/accounts", body).then(function (res) {
      secretBox("Compte créé", res.account.login, res.temporaryPassword); f.reset(); syncRole(); loadAccounts();
    }).catch(function (e) { if (e.status === 401 || e.status === 403) authLoss(e); else showErr("a-err", msg(e)); }).then(function () { busy(f, false); });
  });

  /* ---------- tableau de bord (Direction) ---------- */
  function row(body, cells) { var tr = el("tr"); cells.forEach(function (c) { tr.appendChild(typeof c === "string" ? el("td", null, c) : c); }); body.appendChild(tr); }
  function emptyRow(body, n, t) { var r = el("tr"), c = el("td", null, t); c.colSpan = n; r.appendChild(c); body.appendChild(r); }
  function loadDash() {
    api("GET", "/api/central/overview").then(function (o) {
      var k = $("#d-kpis"); k.replaceChildren();
      [["Motos enregistrées", o.registered], ["Volées (en cours)", o.stolen], ["Retrouvées", o.recovered], ["Taux de retrouvées", o.recoveryRate + " %"]].forEach(function (t) {
        var d = el("div", "card"); d.appendChild(el("b", null, String(t[1]))); d.firstChild.style.cssText = "font:800 2rem var(--font-display);display:block"; d.appendChild(el("span", "note", t[0])); k.appendChild(d);
      });
      var cb = $("#d-communes"); cb.replaceChildren();
      o.communes.forEach(function (c) { row(cb, [c.commune, String(c.registered), String(c.stolen), String(c.recovered)]); });
      var ab = $("#d-active"); ab.replaceChildren();
      if (!o.activeThefts.length) emptyRow(ab, 5, "Aucune moto volée en cours.");
      o.activeThefts.forEach(function (t) {
        var p = el("td"); p.appendChild(el("b", "mono", t.plate));
        var ow = el("td"); ow.appendChild(document.createTextNode(t.ownerName)); ow.appendChild(el("small", "mono", phone(t.ownerPhone)));
        var pl = el("td"); pl.appendChild(document.createTextNode(t.place)); pl.appendChild(el("small", null, t.commune));
        row(ab, [p, t.brand, fmtDate(t.theftDate), pl, ow]);
      });
      var mb = $("#d-coms"); mb.replaceChildren();
      if (!o.commissariats.length) emptyRow(mb, 3, "Aucun commissariat n’a encore de compte.");
      o.commissariats.forEach(function (c) { row(mb, [c.name || "—", c.login + (c.active ? "" : " (suspendu)"), fmtDT(c.lastLoginAt)]); });
      $("#d-updated").textContent = "Actualisé à " + new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
    }).catch(authLoss);
  }
  $("#d-refresh").addEventListener("click", loadDash);

  /* ---------- démarrage ---------- */
  api("GET", "/api/auth/csrf").catch(function () {}).then(function () { return api("GET", "/api/auth/me"); })
    .then(function (m) { me = m; route(); }, function () { me = null; route(); });
})();
