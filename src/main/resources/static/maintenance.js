"use strict";
(function () {
  var $ = function (id) { return document.getElementById(id); };
  var ERR = {
    INVALID_CREDENTIALS: "Email ou mot de passe incorrect.",
    LOCKED: "Compte verrouillé après trop d’essais. Réessayez dans 15 minutes.",
    SUSPENDED: "Ce compte est suspendu.",
    WRONG_CURRENT_PASSWORD: "Le mot de passe actuel est incorrect.",
    WEAK_PASSWORD: "Nouveau mot de passe refusé : 12 caractères minimum, différent de l’ancien.",
    INVALID_LOGIN: "Identifiant invalide (email pour une direction ou un commissariat, matricule pour un agent).",
    COMMISSARIAT_REQUIRED: "Indiquez le commissariat.",
    ALREADY_EXISTS: "Ce compte existe déjà.",
    ROLE_NOT_ALLOWED: "Action non autorisée sur ce niveau.",
    NOT_FOUND: "Compte introuvable."
  };
  var ROLES = { MAINTENANCE: "Maintenance", CENTRAL: "Direction générale", COMMISSARIAT: "Commissariat", AGENT: "Agent" };
  var me = null;
  var toastTimer;

  function cookie(name) {
    var m = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
    return m ? decodeURIComponent(m[1]) : "";
  }
  function api(method, url, body) {
    var h = { "Accept": "application/json" };
    if (body !== undefined) h["Content-Type"] = "application/json";
    var t = cookie("XSRF-TOKEN");
    if (t) h["X-XSRF-TOKEN"] = t;
    return fetch(url, { method: method, headers: h, credentials: "same-origin", body: body === undefined ? undefined : JSON.stringify(body) })
      .then(function (r) {
        return r.text().then(function (txt) {
          var data = null;
          try { data = txt ? JSON.parse(txt) : null; } catch (e) { data = null; }
          if (!r.ok) { var e = new Error((data && data.error) || "HTTP_" + r.status); e.status = r.status; throw e; }
          return data;
        });
      });
  }
  function msg(e) { return ERR[e.message] || "Une erreur est survenue (" + e.message + ")."; }
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }
  function toast(t) {
    var box = $("toast"); box.firstElementChild.textContent = t; box.classList.add("show");
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { box.classList.remove("show"); }, 3200);
  }
  function fmt(iso) {
    if (!iso) return "—";
    var d = new Date(iso);
    return isNaN(d) ? "—" : d.toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" });
  }
  function dur(s) {
    var d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60);
    return (d ? d + " j " : "") + (h || d ? h + " h " : "") + m + " min";
  }
  function show(view) {
    ["view-login", "view-password", "view-app"].forEach(function (v) { $(v).hidden = v !== view; });
    $("userbar").hidden = view === "view-login";
  }
  function showErr(id, text) { var n = $(id); n.textContent = text || ""; n.hidden = !text; }
  function busy(form, on) {
    var b = form.querySelector("button[type=submit]"); if (b) b.disabled = on;
  }

  /* ---------- Navigation ---------- */
  function route() {
    if (!me) { show("view-login"); return; }
    if (me.role !== "MAINTENANCE") {
      show("view-login");
      showErr("l-err", "Ce compte n’a pas accès à la maintenance.");
      api("POST", "/api/auth/logout").catch(function () {}); me = null; return;
    }
    $("who").textContent = me.name;
    if (me.mustChangePassword) { openPassword(true); return; }
    show("view-app"); selectTab("overview");
  }
  function selectTab(name) {
    document.querySelectorAll("[data-tab]").forEach(function (b) {
      var on = b.getAttribute("data-tab") === name;
      b.setAttribute("aria-selected", on ? "true" : "false"); b.tabIndex = on ? 0 : -1;
    });
    document.querySelectorAll("[data-panel]").forEach(function (p) { p.hidden = p.getAttribute("data-panel") !== name; });
    if (name === "overview") loadOverview();
    if (name === "accounts") loadAccounts();
    if (name === "audit") loadAudit();
    if (name === "me") $("me-info").textContent = me.name + " · " + me.login;
  }
  document.querySelectorAll("[data-tab]").forEach(function (b) {
    b.addEventListener("click", function () { selectTab(b.getAttribute("data-tab")); });
    b.addEventListener("keydown", function (ev) {
      var tabs = Array.prototype.slice.call(document.querySelectorAll("[data-tab]"));
      var i = tabs.indexOf(b), n = null;
      if (ev.key === "ArrowRight") n = tabs[(i + 1) % tabs.length];
      if (ev.key === "ArrowLeft") n = tabs[(i - 1 + tabs.length) % tabs.length];
      if (n) { ev.preventDefault(); n.focus(); selectTab(n.getAttribute("data-tab")); }
    });
  });

  /* ---------- Connexion ---------- */
  $("login-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("l-err", "");
    var f = ev.target, login = $("l-login").value.trim(), pass = $("l-pass").value;
    if (!login || !pass) { showErr("l-err", "Saisissez l’email et le mot de passe."); return; }
    busy(f, true);
    api("POST", "/api/auth/login", { login: login, password: pass }).then(function (m) {
      me = m; $("l-pass").value = ""; route();
    }).catch(function (e) { showErr("l-err", msg(e)); }).then(function () { busy(f, false); });
  });
  $("logout").addEventListener("click", function () {
    api("POST", "/api/auth/logout").catch(function () {}).then(function () { me = null; show("view-login"); });
  });

  /* ---------- Mot de passe ---------- */
  function openPassword(forced) {
    ["p-cur", "p-new", "p-new2"].forEach(function (i) { $(i).value = ""; });
    showErr("p-err", "");
    $("pw-title").textContent = forced ? "Choisissez votre mot de passe" : "Changer le mot de passe";
    $("p-cancel").hidden = !!forced;
    show("view-password"); $("p-cur").focus();
  }
  $("open-password").addEventListener("click", function () { openPassword(false); });
  $("p-cancel").addEventListener("click", route);
  $("pw-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("p-err", "");
    var f = ev.target, cur = $("p-cur").value, n1 = $("p-new").value, n2 = $("p-new2").value;
    if (n1.length < 12) { showErr("p-err", "12 caractères minimum."); return; }
    if (n1 !== n2) { showErr("p-err", "Les deux mots de passe ne sont pas identiques."); return; }
    busy(f, true);
    api("POST", "/api/auth/password", { current: cur, next: n1 }).then(function (m) {
      me = m; toast("Mot de passe modifié."); route();
    }).catch(function (e) { showErr("p-err", msg(e)); }).then(function () { busy(f, false); });
  });

  /* ---------- Vue d'ensemble ---------- */
  function dl(target, rows) {
    target.replaceChildren();
    rows.forEach(function (r) {
      var d = el("div"); d.appendChild(el("dt", null, r[0])); d.appendChild(el("dd", null, String(r[1]))); target.appendChild(d);
    });
  }
  function loadOverview() {
    api("GET", "/api/maintenance/overview").then(function (o) {
      var k = $("kpis"); k.replaceChildren();
      var db = o.database.ok;
      var tiles = [
        ["Base de données", db ? "En ligne" : "Hors ligne"],
        ["Motos enregistrées", o.data.motos],
        ["Vols déclarés", o.data.thefts],
        ["Motos volées (en cours)", o.data.motosStolen],
        ["Motos retrouvées", o.data.motosRecovered]
      ];
      tiles.forEach(function (t) { var d = el("div", "kpi"); d.appendChild(el("b", null, String(t[1]))); d.appendChild(el("span", null, t[0])); k.appendChild(d); });
      dl($("server"), [["Version", o.app.version], ["Java", o.app.java], ["Démarré le", fmt(o.app.startedAt)], ["En marche depuis", dur(o.app.uptimeSeconds)], ["Base", o.database.product]]);
      var pct = Math.min(100, Math.round(o.app.memoryUsedMb * 100 / o.app.memoryMaxMb));
      var mem = $("mem"); mem.replaceChildren();
      mem.appendChild(el("p", "note", "Mémoire : " + o.app.memoryUsedMb + " / " + o.app.memoryMaxMb + " Mo (" + pct + " %)"));
      var bar = el("div", "bar"); var i = el("i", pct > 85 ? "hot" : ""); i.style.width = pct + "%"; bar.appendChild(i); mem.appendChild(bar);
      dl($("security"), [["Connexions échouées", o.security.failedLogins24h], ["Comptes verrouillés", o.security.lockouts24h]]);
      dl($("roles"), Object.keys(o.data.accounts).map(function (r) { return [ROLES[r] || r, o.data.accounts[r]]; }));
      $("updated").textContent = "Actualisé à " + new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
    }).catch(handleAuthLoss);
  }
  $("refresh").addEventListener("click", loadOverview);

  function handleAuthLoss(e) {
    if (e && (e.status === 401 || e.status === 403)) { me = null; show("view-login"); showErr("l-err", "Session expirée. Reconnectez-vous."); }
    else toast(msg(e));
  }

  /* ---------- Comptes ---------- */
  function syncRole() {
    var r = $("a-role").value;
    $("a-com-field").hidden = r === "CENTRAL";
    $("a-login-label").textContent = r === "AGENT" ? "Matricule" : "Email";
  }
  $("a-role").addEventListener("change", syncRole); syncRole();

  function secretBox(title, login, pw) {
    var b = $("secret"); b.replaceChildren();
    b.appendChild(el("b", null, title + " : " + login));
    b.appendChild(el("p", "note", "Mot de passe provisoire, affiché une seule fois. Transmettez-le de façon sûre ; il devra être changé à la première connexion."));
    b.appendChild(el("div", "pw", pw));
    var row = el("div", "actions");
    var c = el("button", "btn btn-ghost btn-sm", "Copier"); c.type = "button";
    c.addEventListener("click", function () {
      if (navigator.clipboard) navigator.clipboard.writeText(pw).then(function () { toast("Copié."); }, function () { toast("Sélectionnez le texte pour le copier."); });
    });
    var x = el("button", "btn btn-ghost btn-sm", "Masquer"); x.type = "button";
    x.addEventListener("click", function () { b.hidden = true; b.replaceChildren(); });
    row.appendChild(c); row.appendChild(x); b.appendChild(row); b.hidden = false; b.scrollIntoView({ block: "nearest" });
  }

  function loadAccounts() {
    api("GET", "/api/maintenance/accounts").then(function (list) {
      var body = $("acc-body"); body.replaceChildren();
      list.forEach(function (a) {
        var tr = el("tr");
        var c1 = el("td"); c1.appendChild(el("b", null, a.name)); c1.appendChild(el("small", "mono", a.login));
        if (a.commissariat) c1.appendChild(el("small", null, a.commissariat));
        var c2 = el("td", null, ROLES[a.role] || a.role);
        var c3 = el("td");
        var locked = a.lockedUntil && new Date(a.lockedUntil) > new Date();
        c3.appendChild(el("span", "pill " + (!a.active ? "pill-bad" : locked ? "pill-warn" : "pill-ok"), !a.active ? "Suspendu" : locked ? "Verrouillé" : "Actif"));
        if (a.mustChangePassword) c3.appendChild(el("small", null, "Mot de passe provisoire"));
        var c4 = el("td", null, fmt(a.lastLoginAt));
        var c5 = el("td"), act = el("div", "row-actions");
        if (a.role !== "MAINTENANCE") {
          var t = el("button", "btn btn-ghost btn-sm", a.active ? "Suspendre" : "Réactiver"); t.type = "button";
          t.addEventListener("click", function () {
            api("POST", "/api/maintenance/accounts/" + a.id + "/active", { active: !a.active }).then(function () { toast(a.active ? "Compte suspendu." : "Compte réactivé."); loadAccounts(); }).catch(handleAuthLoss);
          });
          var r = el("button", "btn btn-ghost btn-sm", "Nouveau mot de passe"); r.type = "button";
          r.addEventListener("click", function () {
            if (r.dataset.arm !== "1") { r.dataset.arm = "1"; r.textContent = "Confirmer ?"; setTimeout(function () { r.dataset.arm = ""; r.textContent = "Nouveau mot de passe"; }, 4000); return; }
            api("POST", "/api/maintenance/accounts/" + a.id + "/reset-password").then(function (res) { secretBox("Nouveau mot de passe", a.login, res.temporaryPassword); loadAccounts(); }).catch(handleAuthLoss);
          });
          act.appendChild(t); act.appendChild(r);
        } else act.appendChild(el("small", null, "Compte protégé"));
        c5.appendChild(act);
        [c1, c2, c3, c4, c5].forEach(function (c) { tr.appendChild(c); });
        body.appendChild(tr);
      });
    }).catch(handleAuthLoss);
  }
  $("acc-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("a-err", "");
    var f = ev.target, role = $("a-role").value;
    var body = { login: $("a-login").value.trim(), name: $("a-name").value.trim(), role: role, commissariat: role === "CENTRAL" ? "" : $("a-com").value.trim() };
    if (!body.login || !body.name) { showErr("a-err", "Renseignez l’identifiant et le nom."); return; }
    busy(f, true);
    api("POST", "/api/maintenance/accounts", body).then(function (res) {
      secretBox("Compte créé", res.account.login, res.temporaryPassword);
      f.reset(); syncRole(); loadAccounts();
    }).catch(function (e) { if (e.status === 401 || e.status === 403) handleAuthLoss(e); else showErr("a-err", msg(e)); })
      .then(function () { busy(f, false); });
  });

  /* ---------- Journal ---------- */
  function loadAudit() {
    api("GET", "/api/maintenance/audit?limit=100").then(function (list) {
      var body = $("audit-body"); body.replaceChildren();
      list.forEach(function (l) {
        var tr = el("tr");
        tr.appendChild(el("td", null, fmt(l.at)));
        tr.appendChild(el("td", "mono", l.actor || "—"));
        tr.appendChild(el("td", null, l.action));
        tr.appendChild(el("td", null, l.detail || ""));
        tr.appendChild(el("td", "mono", l.ip || ""));
        body.appendChild(tr);
      });
      if (!list.length) { var r = el("tr"); var c = el("td", null, "Aucun événement."); c.colSpan = 5; r.appendChild(c); body.appendChild(r); }
    }).catch(handleAuthLoss);
  }

  /* ---------- Démarrage ---------- */
  api("GET", "/api/auth/csrf").catch(function () {}).then(function () {
    return api("GET", "/api/auth/me");
  }).then(function (m) { me = m; route(); }, function () { me = null; route(); });
})();
