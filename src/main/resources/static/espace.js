"use strict";
(function () {
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var ERR = {
    INVALID_CREDENTIALS: "Téléphone ou mot de passe incorrect.",
    TOO_MANY_REQUESTS: "Trop de tentatives. Patientez quelques minutes.",
    LOCKED: "Compte verrouillé après trop d’essais. Réessayez dans 15 minutes.",
    SUSPENDED: "Ce compte est suspendu. Contactez votre responsable.",
    WRONG_CURRENT_PASSWORD: "Le mot de passe actuel est incorrect.",
    WEAK_PASSWORD: "Mot de passe refusé : 10 caractères minimum, avec des lettres.",
    NOT_STOLEN: "Cette moto n’est plus signalée volée.",
    COMMISSARIAT_REQUIRED: "Indiquez le commissariat.",
    SECTION_REQUIRED: "Indiquez le bureau ou la section.",
    ALREADY_EXISTS: "Un compte existe déjà avec ce numéro de téléphone.",
    INVALID_PHONE: "Numéro de téléphone invalide : 8 chiffres attendus.",
    INVALID_NAME: "Indiquez le nom de la personne.",
    ROLE_NOT_ALLOWED: "Vous ne pouvez pas créer ce niveau de compte.",
    NOT_FOUND: "Introuvable.",
    INVALID_COMMUNE: "Commune invalide.",
    INVALID_YEAR: "Année invalide.",
    INVALID_DATE: "Date invalide (pas dans le futur, moins d’un an).",
    INVALID_NUMBERS: "Plaque, châssis ou moteur trop courts.",
    INVALID_PAYMENT_MODE: "Mode de paiement invalide.",
    CHECKS_REQUIRED: "Cochez les deux contrôles avant d’enregistrer.",
    SAME_CHECKER: "Le second agent doit être une autre personne que vous.",
    CHECKER_UNKNOWN: "Le second agent doit avoir un compte actif dans votre bureau. Vérifiez son nom exact.",
    RESP_ONLY: "Seul le responsable du bureau peut refaire un code secret.",
    PLATE_EXISTS: "Cette plaque est déjà enregistrée.",
    CHASSIS_EXISTS: "Ce numéro de châssis est déjà enregistré.",
    ENGINE_EXISTS: "Ce numéro de moteur est déjà enregistré.",
    ALREADY_STOLEN: "Cette moto est déjà déclarée volée."
  };
  var ROLE = { CENTRAL: "Direction générale", COMMISSARIAT: "Commissariat", AGENT: "Agent de terrain", BUREAU_RESP: "Responsable de bureau", BUREAU_AGENT: "Agent de bureau" };
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
  function isBureau() { return me && (me.role === "BUREAU_RESP" || me.role === "BUREAU_AGENT"); }
  function route() {
    if (!me) { show("view-login"); return; }
    if (me.role === "MAINTENANCE") { location.replace("maintenance.html"); return; }
    if (me.mustChangePassword) { ["p-cur", "p-new", "p-new2"].forEach(function (i) { $("#" + i).value = ""; }); showErr("p-err", ""); show("view-password"); $("#p-cur").focus(); return; }
    var bureau = isBureau();
    $("#who").textContent = me.name + (me.commissariat ? " · " + me.commissariat : "") + (me.section ? " · " + me.section : "");
    $("#role-label").textContent = ROLE[me.role] || me.role;
    $("#app-title").textContent = me.role === "CENTRAL" ? "Direction générale" : bureau ? "Bureau d’enregistrement" : "Espace police";
    $("#tab-dash").hidden = me.role !== "CENTRAL";
    $("#tab-alertes").hidden = bureau;
    $("#tab-enreg").hidden = !bureau;
    $("#tab-vol").hidden = !bureau;
    $("#rec-com-field").hidden = me.role !== "CENTRAL";
    var canAdmin = me.role === "CENTRAL" || me.role === "COMMISSARIAT" || me.role === "BUREAU_RESP";
    $("#tab-comptes").hidden = !canAdmin;
    $("#a-role-field").hidden = me.role !== "CENTRAL";
    $("#a-com-field").hidden = me.role !== "CENTRAL";
    $("#acc-title").textContent = me.role === "CENTRAL" ? "Créer un compte commissariat ou agent" : me.role === "COMMISSARIAT" ? "Créer un compte agent pour votre commissariat" : "Créer un compte agent pour votre bureau";
    syncRole();
    show("view-app"); selectTab(bureau ? "enreg" : "verif");
  }
  function selectTab(name) {
    $$("[data-tab]").forEach(function (b) { var on = b.getAttribute("data-tab") === name; b.setAttribute("aria-selected", on ? "true" : "false"); b.tabIndex = on ? 0 : -1; });
    $$("[data-panel]").forEach(function (p) { p.hidden = p.getAttribute("data-panel") !== name; });
    closeRec();
    if (name === "alertes") loadAlerts();
    if (name === "dash") loadDash();
    if (name === "comptes") loadAccounts();
    if (name === "enreg") loadMine();
    if (name === "vol") { var d = $("#v-date"); d.max = new Date().toISOString().slice(0, 10); if (!d.value) d.value = d.max; }
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
    if (!l || !p) { showErr("l-err", "Saisissez votre numéro de téléphone et le mot de passe."); return; }
    busy(f, true);
    api("POST", "/api/auth/login", { login: l, password: p }).then(function (m) { me = m; $("#l-pass").value = ""; route(); })
      .catch(function (e) { showErr("l-err", msg(e)); }).then(function () { busy(f, false); });
  });
  $("#logout").addEventListener("click", function () { api("POST", "/api/auth/logout").catch(function () {}).then(function () { me = null; show("view-login"); }); });
  $("#pw-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("p-err", ""); var f = ev.target, n1 = $("#p-new").value;
    if (n1.length < 10) { showErr("p-err", "10 caractères minimum."); return; }
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
  ["r-commune", "v-commune"].forEach(function (id) { var sel = $("#" + id); sel.appendChild(new Option("Choisir", "")); COMMUNES.forEach(function (c) { sel.appendChild(new Option(c, c)); }); });
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


  /* ---------- comptes (par liens d'invitation) ---------- */
  var ROLES = { COMMISSARIAT: "Commissariat", AGENT: "Agent de terrain", BUREAU_AGENT: "Agent de bureau" };
  function curRole() { return me.role === "CENTRAL" ? $("#a-role").value : me.role === "COMMISSARIAT" ? "AGENT" : "BUREAU_AGENT"; }
  function syncRole() {
    if (!me) return;
    $("#a-com-field").hidden = me.role !== "CENTRAL";
  }
  $("#a-role").addEventListener("change", syncRole);
  function copyText(t, ok) {
    if (navigator.clipboard) navigator.clipboard.writeText(t).then(function () { toast(ok || "Copié."); }, function () { toast("Sélectionnez le lien pour le copier."); });
    else toast("Sélectionnez le lien pour le copier.");
  }
  function linkBox(title, name, phoneNum, url, expires) {
    var b = $("#secret"); b.replaceChildren();
    b.appendChild(el("b", null, title + " : " + name));
    b.appendChild(el("p", "note", "Lien personnel, valable jusqu’au " + fmtDT(expires) + " et utilisable une seule fois. Envoyez-le uniquement à cette personne."));
    var u = el("div", "mono", url); u.style.cssText = "font-size:.85rem;user-select:all;overflow-wrap:anywhere;margin:6px 0"; b.appendChild(u);
    var row = el("div", "actions");
    var c = el("button", "btn btn-primary btn-sm", "Copier le lien"); c.type = "button"; c.addEventListener("click", function () { copyText(url, "Lien copié."); });
    var w = el("a", "btn btn-secondary btn-sm", "Envoyer par WhatsApp"); w.target = "_blank"; w.rel = "noopener";
    w.href = "https://wa.me/223" + phoneNum + "?text=" + encodeURIComponent("Bonjour " + name + ", voici votre lien personnel MotoSûr Mali pour ouvrir votre compte (valable 48 h, utilisable une seule fois) : " + url);
    var x = el("button", "btn btn-ghost btn-sm", "Masquer"); x.type = "button"; x.addEventListener("click", function () { b.hidden = true; b.replaceChildren(); });
    row.appendChild(c); row.appendChild(w); row.appendChild(x); b.appendChild(row); b.hidden = false; b.scrollIntoView({ block: "nearest" });
  }
  function loadAccounts() {
    api("GET", "/api/admin/accounts").then(function (list) {
      var body = $("#acc-body"); body.replaceChildren();
      if (!list.length) { emptyRow(body, 5, "Aucun compte pour le moment."); return; }
      list.forEach(function (a) {
        var c1 = el("td"); c1.appendChild(el("b", null, a.name)); c1.appendChild(el("small", "mono", phone(a.phone || a.login))); if (a.commissariat) c1.appendChild(el("small", null, a.commissariat)); if (a.section) c1.appendChild(el("small", null, a.section));
        var c3 = el("td"), locked = a.lockedUntil && new Date(a.lockedUntil) > new Date();
        c3.appendChild(el("span", "pill " + (!a.active ? "pill-bad" : (a.pending || locked) ? "pill-warn" : "pill-ok"), !a.active ? "Suspendu" : a.pending ? "En attente du lien" : locked ? "Verrouillé" : "Actif"));
        var c5 = el("td"), act = el("div", "actions");
        var t = el("button", "btn btn-ghost btn-sm", a.active ? "Suspendre" : "Réactiver"); t.type = "button";
        t.addEventListener("click", function () { api("POST", "/api/admin/accounts/" + a.id + "/active", { active: !a.active }).then(function () { toast(a.active ? "Compte suspendu." : "Compte réactivé."); loadAccounts(); }).catch(authLoss); });
        var r = el("button", "btn btn-ghost btn-sm", "Nouveau lien"); r.type = "button";
        r.addEventListener("click", function () {
          if (r.dataset.arm !== "1") { r.dataset.arm = "1"; r.textContent = "Confirmer ?"; setTimeout(function () { r.dataset.arm = ""; r.textContent = "Nouveau lien"; }, 4000); return; }
          api("POST", "/api/admin/accounts/" + a.id + "/invite").then(function (res) { linkBox("Nouveau lien", a.name, a.phone || a.login, res.inviteUrl, res.expiresAt); loadAccounts(); }).catch(authLoss);
        });
        act.appendChild(t); act.appendChild(r); c5.appendChild(act);
        row(body, [c1, ROLES[a.role] || a.role, c3, fmtDT(a.lastLoginAt), c5]);
      });
    }).catch(authLoss);
  }
  $("#acc-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("a-err", ""); var f = ev.target, role = curRole();
    var body = { name: $("#a-name").value.trim(), phone: $("#a-phone").value.trim(), role: role, commissariat: me.role === "CENTRAL" ? $("#a-com").value.trim() : "" };
    if (!body.name || !body.phone) { showErr("a-err", "Renseignez le nom et le téléphone."); return; }
    if (me.role === "CENTRAL" && !body.commissariat) { showErr("a-err", "Indiquez le commissariat."); return; }
    busy(f, true);
    api("POST", "/api/admin/accounts", body).then(function (res) {
      linkBox("Compte créé", res.account.name, res.account.phone, res.inviteUrl, res.expiresAt); f.reset(); syncRole(); loadAccounts();
    }).catch(function (e) { if (e.status === 401 || e.status === 403) authLoss(e); else showErr("a-err", msg(e)); }).then(function () { busy(f, false); });
  });

  /* ---------- impression ---------- */
  function printNode(node) {
    var z = $("#print-zone"); z.replaceChildren(node); document.body.classList.add("printing"); window.print();
  }
  window.addEventListener("afterprint", function () { document.body.classList.remove("printing"); $("#print-zone").replaceChildren(); });
  function pzDl(rows) {
    var d = el("dl");
    rows.forEach(function (r) { d.appendChild(el("dt", null, r[0])); d.appendChild(el("dd", null, String(r[1]))); });
    return d;
  }
  function receiptNode(d) {
    var n = el("div");
    var hd = el("div", "pz-head"); hd.appendChild(el("small", null, "République du Mali · Registre des motos")); hd.appendChild(el("h1", null, "MotoSûr Mali · Reçu d’enregistrement")); n.appendChild(hd);
    n.appendChild(el("p", "pz-sub", d.receipt + " · " + fmtDT(d.registeredAt) + " · " + (d.section || "")));
    n.appendChild(pzDl([["Propriétaire", d.ownerName], ["Téléphone", phone(d.ownerPhone)], ["Plaque", d.plate], ["Marque et modèle", d.brand + " " + d.model + " · " + d.color + " · " + d.year], ["N° de châssis", d.chassis], ["N° de moteur", d.engine], ["Montant payé", d.amount + " FCFA (" + d.paymentMode + ")"], ["Enregistré par", d.agent + " · relu par " + d.checkedBy]]));
    n.appendChild(el("h2", null, "Numéro d’enregistrement : " + d.reference));
    n.appendChild(el("div", "pz-code", d.pin));
    n.appendChild(el("p", "pz-note", "Gardez ce reçu. Le numéro d’enregistrement et le code secret permettent de déclarer un vol ou de lever l’alerte. Ne donnez le code à personne."));
    var sg = el("div", "pz-sign"); sg.appendChild(el("div", null, "Signature du propriétaire")); sg.appendChild(el("div", null, "Cachet et signature du bureau")); n.appendChild(sg);
    n.appendChild(el("p", "pz-foot", "MotoSûr Mali · Ce reçu prouve l’enregistrement de la moto au registre. Il ne remplace pas les papiers de la moto."));
    return n;
  }

  /* ---------- bureau : enregistrement ---------- */
  function reqVal(id) { return $("#" + id).value.trim(); }
  $("#reg-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("r-err", ""); var f = ev.target;
    var need = ["r-owner", "r-phone", "r-commune", "r-plate", "r-chassis", "r-engine", "r-brand", "r-model", "r-color", "r-year", "r-checker"];
    for (var k = 0; k < need.length; k++) { if (!reqVal(need[k])) { showErr("r-err", "Renseignez tous les champs."); $("#" + need[k]).focus(); return; } }
    if (!$("#r-chk1").checked || !$("#r-chk2").checked) { showErr("r-err", ERR.CHECKS_REQUIRED); return; }
    var body = { ownerName: reqVal("r-owner"), phone: reqVal("r-phone"), commune: reqVal("r-commune"), plate: reqVal("r-plate"), chassis: reqVal("r-chassis"), engine: reqVal("r-engine"),
      brand: reqVal("r-brand"), model: reqVal("r-model"), color: reqVal("r-color"), year: parseInt(reqVal("r-year"), 10), checkedBy: reqVal("r-checker"),
      amount: parseInt($("#r-amount").value, 10) || 0, paymentMode: $("#r-mode").value, papersSeen: $("#r-chk1").checked, numbersChecked: $("#r-chk2").checked };
    busy(f, true);
    api("POST", "/api/bureau/motos", body).then(function (d) {
      var done = $("#reg-done"); done.replaceChildren();
      done.appendChild(status("ok", "Moto enregistrée et vérifiée en personne", "Remettez le reçu au propriétaire."));
      var ref = el("div", "cert-ref"); ref.appendChild(el("span", null, "Numéro d’enregistrement")); ref.appendChild(el("span", "mono", d.reference)); done.appendChild(ref);
      var pin = el("div", "cert-ref"); pin.appendChild(el("span", null, "Code secret")); var pv = el("span", "mono", d.pin); pv.style.cssText = "font-size:1.6rem;letter-spacing:.3em"; pin.appendChild(pv); done.appendChild(pin);
      done.appendChild(dl([["Reçu", d.receipt, 1], ["Plaque", d.plate, 1], ["Propriétaire", d.ownerName], ["Téléphone", phone(d.ownerPhone), 1], ["Montant payé", d.amount + " FCFA · " + d.paymentMode], ["Saisi par", d.agent + " · relu par " + d.checkedBy]]));
      done.appendChild(el("p", "note", "Le code secret n’est affiché qu’une fois. N’envoyez le reçu par WhatsApp que si le client le demande : il contient son code secret."));
      var act = el("div", "actions");
      var pr = el("button", "btn btn-primary", "Imprimer le reçu"); pr.type = "button"; pr.addEventListener("click", function () { printNode(receiptNode(d)); });
      var wa = el("a", "btn btn-secondary", "Envoyer le reçu par WhatsApp"); wa.target = "_blank"; wa.rel = "noopener";
      wa.href = "https://wa.me/223" + d.ownerPhone + "?text=" + encodeURIComponent("MotoSûr Mali · Reçu " + d.receipt + "\nMoto " + d.plate + " enregistrée.\nNuméro d’enregistrement : " + d.reference + "\nCode secret : " + d.pin + " (ne le donnez à personne)\nMontant payé : " + d.amount + " FCFA");
      var again = el("button", "btn btn-ghost", "Enregistrer une autre moto"); again.type = "button";
      again.addEventListener("click", function () { f.reset(); $("#r-amount").value = 2000; done.hidden = true; $("#reg-card").hidden = false; $("#r-owner").focus(); });
      act.appendChild(pr); act.appendChild(wa); act.appendChild(again); done.appendChild(act);
      $("#reg-card").hidden = true; done.hidden = false; done.scrollIntoView({ block: "start" }); loadMine();
    }).catch(function (e) { if (e.status === 401 || e.status === 403) authLoss(e); else showErr("r-err", msg(e)); }).then(function () { busy(f, false); });
  });
  function loadMine() {
    api("GET", "/api/bureau/motos/mine").then(function (list) {
      var body = $("#mine-body"); body.replaceChildren();
      if (!list.length) { emptyRow(body, 5, "Aucun enregistrement pour le moment."); return; }
      list.forEach(function (m) {
        var act = el("td"), b = el("button", "btn btn-ghost btn-sm", "Nouveau code secret"); b.type = "button"; if (me.role !== "BUREAU_RESP") b.hidden = true;
        b.addEventListener("click", function () {
          if (b.dataset.arm !== "1") { b.dataset.arm = "1"; b.textContent = "Confirmer ?"; setTimeout(function () { b.dataset.arm = ""; b.textContent = "Nouveau code secret"; }, 4000); return; }
          api("POST", "/api/bureau/motos/" + m.id + "/reset-pin").then(function (d) {
            var box = $("#code-box"); box.replaceChildren();
            box.appendChild(el("b", null, "Nouveau code pour " + d.plate + " (" + d.reference + ")"));
            var c = el("div", "mono", d.pin); c.style.cssText = "font-size:1.6rem;letter-spacing:.3em;margin:6px 0"; box.appendChild(c);
            box.appendChild(el("p", "note", "L’ancien code ne marche plus. Notez-le sur le reçu du propriétaire."));
            box.hidden = false; box.scrollIntoView({ block: "center" });
          }).catch(authLoss);
        });
        act.appendChild(b);
        row(body, [m.receipt || "—", m.plate, m.ownerName, fmtDT(m.registeredAt), act]);
      });
    }).catch(authLoss);
  }

  /* ---------- bureau : déclaration de vol ---------- */
  $("#vol-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("v-err", ""); var f = ev.target;
    if (!reqVal("v-q") || !reqVal("v-date") || !reqVal("v-commune") || !reqVal("v-place")) { showErr("v-err", "Renseignez la moto, la date, la commune et le lieu."); return; }
    busy(f, true);
    api("POST", "/api/bureau/thefts", { q: reqVal("v-q"), date: reqVal("v-date"), commune: reqVal("v-commune"), place: reqVal("v-place"), details: reqVal("v-details") }).then(function (d) {
      var done = $("#vol-done"); done.replaceChildren(); done.hidden = false;
      done.appendChild(status("stolen", "Alerte enregistrée", "Moto " + d.plate + " · référence " + d.reference));
      f.reset(); $("#v-date").value = $("#v-date").max;
    }).catch(function (e) { if (e.status === 401 || e.status === 403) authLoss(e); else showErr("v-err", msg(e)); }).then(function () { busy(f, false); });
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
