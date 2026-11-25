"use strict";
(function () {
  var $ = function (id) { return document.getElementById(id); };
  var ROLE = { MAINTENANCE: "Maintenance", CENTRAL: "Direction générale de la Police nationale", COMMISSARIAT: "Commissariat", AGENT: "Agent de terrain", BUREAU_RESP: "Responsable de bureau", BUREAU_AGENT: "Agent de bureau" };
  var ERR = { WEAK_PASSWORD: "Mot de passe refusé : 12 caractères minimum, avec des lettres (pas seulement des chiffres).", TOO_MANY_REQUESTS: "Trop de tentatives. Patientez quelques minutes." };
  var token = (location.hash || "").slice(1);

  function cookie(n) { var m = document.cookie.match(new RegExp("(?:^|; )" + n + "=([^;]*)")); return m ? decodeURIComponent(m[1]) : ""; }
  function api(method, url, body) {
    var h = { "Accept": "application/json", "Content-Type": "application/json" };
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
  function view(id) { ["view-loading", "view-bad", "view-form"].forEach(function (v) { $(v).hidden = v !== id; }); }
  function showErr(t) { var n = $("iv-err"); n.textContent = t || ""; n.hidden = !t; }

  if (token.length < 20) { view("view-bad"); return; }
  api("GET", "/api/auth/csrf").catch(function () {}).then(function () { return api("POST", "/api/invitations/check", { token: token }); })
    .then(function (d) {
      $("iv-role").textContent = ROLE[d.role] || "Invitation";
      $("iv-title").textContent = "Bienvenue, " + d.name;
      $("iv-pwbox").hidden = d.role !== "MAINTENANCE";
      view("view-form");
    }, function () { view("view-bad"); });

  $("iv-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); showErr("");
    var needPw = !$("iv-pwbox").hidden, pw = $("iv-pw").value;
    if (needPw && (pw.length < 12 || /^\d+$/.test(pw))) { showErr(ERR.WEAK_PASSWORD); return; }
    if (needPw && pw !== $("iv-pw2").value) { showErr("Les deux mots de passe sont différents."); return; }
    var b = ev.target.querySelector("button[type=submit]"); b.disabled = true;
    api("POST", "/api/invitations/accept", needPw ? { token: token, password: pw } : { token: token }).then(function (me) {
      try { history.replaceState(null, "", location.pathname); } catch (e) {}
      location.replace(me.role === "MAINTENANCE" ? "maintenance.html" : "espace.html");
    }).catch(function (e) {
      if (e.status === 410) { view("view-bad"); return; }
      showErr(ERR[e.message] || "Une erreur est survenue. Réessayez."); b.disabled = false;
    });
  });
})();
