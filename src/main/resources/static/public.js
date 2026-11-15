"use strict";
(function () {
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

  var MSG = {
    TOO_MANY_REQUESTS: "Trop de demandes en peu de temps. Patientez quelques minutes.",
    INVALID_PHONE: "Numéro de téléphone invalide : 8 chiffres attendus.",
    INVALID_COMMUNE: "Commune invalide.",
    INVALID_YEAR: "Année invalide.",
    INVALID_DATE: "Date invalide (pas dans le futur, moins d’un an).",
    INVALID_NUMBERS: "Plaque, châssis ou moteur trop courts.",
    PLATE_EXISTS: "Cette plaque est déjà enregistrée.",
    CHASSIS_EXISTS: "Ce numéro de châssis est déjà enregistré.",
    ENGINE_EXISTS: "Ce numéro de moteur est déjà enregistré.",
    NOT_OWNER: "Moto ou téléphone non reconnus. Vérifiez la plaque et le téléphone enregistré.",
    ALREADY_STOLEN: "Cette moto est déjà déclarée volée.",
    NOT_STOLEN: "Cette moto n’est pas déclarée volée."
  };
  var FIELD_OF = { PLATE_EXISTS: "r-plate", CHASSIS_EXISTS: "r-chassis", ENGINE_EXISTS: "r-engine" };

  function cookie(n) { var m = document.cookie.match(new RegExp("(?:^|; )" + n + "=([^;]*)")); return m ? decodeURIComponent(m[1]) : ""; }
  function api(method, url, body) {
    var h = { "Accept": "application/json" };
    if (body !== undefined) h["Content-Type"] = "application/json";
    var t = cookie("XSRF-TOKEN"); if (t) h["X-XSRF-TOKEN"] = t;
    return fetch(url, { method: method, headers: h, credentials: "same-origin", body: body === undefined ? undefined : JSON.stringify(body) })
      .then(function (r) {
        return r.text().then(function (txt) {
          var d = null; try { d = txt ? JSON.parse(txt) : null; } catch (e) { d = null; }
          if (!r.ok) { var e = new Error((d && d.error) || ("HTTP_" + r.status)); e.status = r.status; throw e; }
          return d;
        });
      });
  }
  function withCsrf(fn) {
    return function () { var a = arguments; return (cookie("XSRF-TOKEN") ? Promise.resolve() : api("GET", "/api/auth/csrf").catch(function () {})).then(function () { return fn.apply(null, a); }); };
  }
  var post = withCsrf(function (u, b) { return api("POST", u, b); });
  function msg(e) { return MSG[e.message] || (e.status === 400 ? "Vérifiez les informations saisies." : "Service momentanément indisponible. Réessayez."); }

  function el(tag, cls, text) { var n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; }
  var toastT;
  function toast(t) { var b = $("#toast"); $("span", b).textContent = t; b.classList.add("show"); clearTimeout(toastT); toastT = setTimeout(function () { b.classList.remove("show"); }, 3200); }
  function fmtDate(v) { if (!v) return "—"; var d = new Date(v); return isNaN(d) ? "—" : d.toLocaleDateString("fr-FR", { day: "2-digit", month: "long", year: "numeric" }); }

  function setErr(id, text) {
    var p = $("#err-" + id), i = $("#" + id);
    if (p) { p.textContent = text || ""; p.hidden = !text; }
    if (i) { if (text) i.setAttribute("aria-invalid", "true"); else i.removeAttribute("aria-invalid"); }
  }
  function clearErrs(form) { $$(".err", form).forEach(function (p) { p.hidden = true; p.textContent = ""; }); $$("[aria-invalid]", form).forEach(function (i) { i.removeAttribute("aria-invalid"); }); }
  function busy(form, on) { var b = $("button[type=submit]", form); if (b) b.disabled = on; }
  function val(id) { return $("#" + id).value.trim(); }

  var ICON = {
    ok: '<path d="M8 12.5l3 3 5-6"/><circle cx="12" cy="12" r="9"/>',
    stolen: '<path d="M12 3l10 18H2L12 3z"/><path d="M12 10v5M12 18v.01"/>',
    unknown: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.6 2.2c-.7.4-1.1.9-1.1 1.8M12 17v.01"/>'
  };
  function status(kind, title, subParts) {
    var box = el("div", "status status-" + kind);
    var ic = el("span", "status-ic");
    ic.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + ICON[kind] + "</svg>";
    var d = el("div"); d.appendChild(el("div", "status-t", title));
    var s = el("div", "status-s"); (subParts || []).forEach(function (p) { s.appendChild(typeof p === "string" ? document.createTextNode(p) : p); });
    d.appendChild(s); box.appendChild(ic); box.appendChild(d); return box;
  }
  function dl(rows) {
    var d = el("dl", "dl");
    rows.forEach(function (r) { var c = el("div"); c.appendChild(el("dt", null, r[0])); c.appendChild(el("dd", r[2] ? "mono" : null, String(r[1]))); d.appendChild(c); });
    return d;
  }
  function refBlock(label, ref, id) {
    var b = el("div", "cert-ref"); b.appendChild(el("span", null, label));
    var m = el("span", "mono", ref); m.id = id; b.appendChild(m);
    var c = el("button", "btn btn-secondary btn-sm", "Copier"); c.type = "button";
    c.addEventListener("click", function () {
      if (navigator.clipboard) navigator.clipboard.writeText(ref).then(function () { toast("Référence copiée"); }, function () { toast("Copiez la référence manuellement"); });
      else toast("Copiez la référence manuellement");
    });
    b.appendChild(c); return b;
  }

  /* ---------- routage ---------- */
  var VIEWS = ["accueil", "enregistrer", "verifier", "signaler"];
  function route() {
    var v = (location.hash || "#accueil").slice(1); if (VIEWS.indexOf(v) < 0) v = "accueil";
    $$("[data-view]").forEach(function (s) { if (s.tagName === "SECTION") s.hidden = s.getAttribute("data-view") !== v; });
    $$("#nav a").forEach(function (a) { if (a.getAttribute("data-view") === v) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current"); });
    $("#nav").classList.remove("open"); $("#navToggle").setAttribute("aria-expanded", "false");
    window.scrollTo(0, 0);
  }
  window.addEventListener("hashchange", route);
  $("#navToggle").addEventListener("click", function () { var o = $("#nav").classList.toggle("open"); this.setAttribute("aria-expanded", o ? "true" : "false"); });

  /* ---------- communes ---------- */
  var COMMUNES = ["Commune I", "Commune II", "Commune III", "Commune IV", "Commune V", "Commune VI"];
  ["r-commune", "s-commune"].forEach(function (id) {
    var s = $("#" + id); if (!s) return;
    s.appendChild(new Option("Choisir", ""));
    COMMUNES.forEach(function (c) { s.appendChild(new Option(c, c)); });
  });
  $("#s-date").max = new Date().toISOString().slice(0, 10);
  $("#r-year").max = new Date().getFullYear() + 1;

  /* ---------- vérification ---------- */
  function resultNode(d, q) {
    var box = el("div", "result");
    if (!d.found) {
      var b = el("b", "mono", q.toUpperCase());
      box.appendChild(status("unknown", "Non enregistrée", ["Le numéro ", b, " ne figure pas dans le registre."]));
      box.appendChild(el("p", "note", "Cela ne veut pas dire que la moto est volée : elle n’a simplement pas été enregistrée. Demandez les papiers au vendeur."));
      return box;
    }
    var rows = [["Plaque", d.plate, 1], ["Marque et modèle", d.brand + " " + d.model], ["Couleur", d.color], ["Année", d.year], ["N° de châssis", d.chassis, 1], ["N° de moteur", d.engine, 1]];
    if (d.status === "STOLEN") {
      box.appendChild(status("stolen", "Signalée volée", ["Vol déclaré le " + fmtDate(d.theftDate) + " · " + (d.theftCommune || "") + "."]));
      box.appendChild(dl(rows));
      var c = el("div", "callout"); c.appendChild(el("b", null, "N’achetez pas cette moto.")); c.appendChild(document.createTextNode(" Prévenez le commissariat le plus proche."));
      box.appendChild(c);
    } else if (d.status === "RECOVERED") {
      box.appendChild(status("ok", "En règle", ["Retrouvée le " + fmtDate(d.recoveredAt) + ". Elle n’est plus signalée volée."]));
      box.appendChild(dl(rows));
    } else {
      box.appendChild(status("ok", "En règle", ["Moto enregistrée, aucune déclaration de vol en cours."]));
      box.appendChild(dl(rows));
    }
    return box;
  }
  function bindCheck(formId, inputId, resultId) {
    $(formId).addEventListener("submit", function (ev) {
      ev.preventDefault();
      var q = $(inputId).value.trim(), out = $(resultId); out.replaceChildren();
      if (q.replace(/[^A-Za-z0-9]/g, "").length < 4) { out.appendChild(el("p", "err", "Saisissez au moins 4 caractères.")); return; }
      var f = ev.target; busy(f, true);
      api("GET", "/api/public/check?q=" + encodeURIComponent(q)).then(function (d) { out.replaceChildren(resultNode(d, q)); })
        .catch(function (e) { out.replaceChildren(el("p", "err", msg(e))); }).then(function () { busy(f, false); });
    });
  }
  bindCheck("#hero-form", "#hero-q", "#hero-result");
  bindCheck("#pub-form", "#pub-q", "#pub-result");

  /* ---------- enregistrement ---------- */
  $("#reg-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); var f = ev.target; clearErrs(f);
    var req = { "r-owner": "Indiquez le nom et prénom.", "r-phone": "Indiquez le téléphone.", "r-commune": "Choisissez la commune.", "r-plate": "Indiquez la plaque.", "r-chassis": "Indiquez le numéro de châssis.", "r-engine": "Indiquez le numéro de moteur.", "r-brand": "Indiquez la marque.", "r-model": "Indiquez le modèle.", "r-color": "Choisissez la couleur.", "r-year": "Indiquez l’année." };
    var bad = false;
    Object.keys(req).forEach(function (id) { if (!val(id)) { setErr(id, req[id]); bad = true; } });
    if (bad) { var first = $("[aria-invalid]", f); if (first) first.focus(); return; }
    var body = { ownerName: val("r-owner"), phone: val("r-phone"), commune: val("r-commune"), plate: val("r-plate"), chassis: val("r-chassis"), engine: val("r-engine"), brand: val("r-brand"), model: val("r-model"), color: val("r-color"), year: parseInt(val("r-year"), 10) };
    busy(f, true);
    post("/api/public/motos", body).then(function (d) {
      var done = $("#reg-done"); done.replaceChildren();
      var s = status("ok", "Moto enregistrée", ["La moto ", el("b", "mono", d.plate), " est maintenant dans le registre."]);
      done.appendChild(s);
      done.appendChild(refBlock("Votre référence", d.reference, "cert-ref-text"));
      done.appendChild(el("p", "note", "Conservez cette référence. Si votre moto est volée, vous pourrez déclarer le vol avec la plaque et votre téléphone."));
      f.reset(); $("#reg-card").hidden = true; done.hidden = false; done.scrollIntoView({ block: "start" });
    }).catch(function (e) {
      var fld = FIELD_OF[e.message];
      if (fld) { setErr(fld, msg(e)); $("#" + fld).focus(); }
      else if (e.message === "INVALID_PHONE") { setErr("r-phone", msg(e)); $("#r-phone").focus(); }
      else toast(msg(e));
    }).then(function () { busy(f, false); });
  });

  /* ---------- déclarer un vol ---------- */
  var foundQ = "";
  $("#s-find-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); var f = ev.target; setErr("s-q", "");
    var q = $("#s-q").value.trim(); foundQ = "";
    $("#s-found").hidden = true; $("#s-step2").hidden = true;
    if (q.replace(/[^A-Za-z0-9]/g, "").length < 4) { setErr("s-q", "Saisissez la plaque ou le numéro de châssis."); return; }
    busy(f, true);
    api("GET", "/api/public/lookup?q=" + encodeURIComponent(q)).then(function (d) {
      if (!d.found) { setErr("s-q", "Aucune moto enregistrée avec ce numéro. Enregistrez-la d’abord."); return; }
      var box = $("#s-found"); box.replaceChildren();
      box.appendChild(el("h2", null, "Moto trouvée"));
      box.appendChild(dl([["Plaque", d.plate, 1], ["Marque et modèle", d.brand + " " + d.model], ["Couleur", d.color], ["Année", d.year]]));
      if (d.status === "STOLEN") { box.appendChild(el("p", "note", "Cette moto est déjà déclarée volée. Si vous l’avez retrouvée, utilisez « Ma moto est retrouvée » plus bas.")); box.hidden = false; return; }
      foundQ = q; box.hidden = false; $("#s-step2").hidden = false;
    }).catch(function (e) { setErr("s-q", msg(e)); }).then(function () { busy(f, false); });
  });
  $("#s-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); var f = ev.target; clearErrs(f);
    var bad = false;
    [["s-date", "Indiquez la date du vol."], ["s-commune", "Choisissez la commune."], ["s-place", "Indiquez le lieu précis."], ["s-phone", "Indiquez le téléphone enregistré."]].forEach(function (p) { if (!val(p[0])) { setErr(p[0], p[1]); bad = true; } });
    if (bad || !foundQ) return;
    busy(f, true);
    post("/api/public/thefts", { q: foundQ, phone: val("s-phone"), date: val("s-date"), commune: val("s-commune"), place: val("s-place"), details: val("s-details") }).then(function (d) {
      var done = $("#s-done"); done.replaceChildren();
      done.appendChild(status("stolen", "Alerte enregistrée", ["Le vol de la moto ", el("b", "mono", d.plate), " est déclaré. La police peut désormais la reconnaître."]));
      done.appendChild(refBlock("Référence de l’alerte", d.reference, "al-ref-text"));
      f.reset(); $("#s-step1").hidden = true; $("#s-found").hidden = true; $("#s-step2").hidden = true; done.hidden = false; done.scrollIntoView({ block: "start" });
    }).catch(function (e) { if (e.message === "NOT_OWNER" || e.message === "INVALID_PHONE") setErr("s-phone", msg(e)); else if (e.message === "INVALID_DATE") setErr("s-date", msg(e)); else toast(msg(e)); })
      .then(function () { busy(f, false); });
  });
  $("#found-form").addEventListener("submit", function (ev) {
    ev.preventDefault(); var f = ev.target; clearErrs(f); var out = $("#found-done"); out.replaceChildren();
    var bad = false;
    [["f-q", "Indiquez la plaque ou le châssis."], ["f-phone", "Indiquez le téléphone enregistré."]].forEach(function (p) { if (!val(p[0])) { setErr(p[0], p[1]); bad = true; } });
    if (bad) return;
    busy(f, true);
    post("/api/public/recovered", { q: val("f-q"), phone: val("f-phone") }).then(function (d) {
      var c = el("div", "cert");
      c.appendChild(status("ok", "Alerte levée", ["La moto ", el("b", "mono", d.plate), " est de nouveau « en règle »."]));
      c.appendChild(refBlock("Référence de confirmation", d.reference, "ret-ref-text"));
      out.appendChild(c); f.reset();
    }).catch(function (e) { if (e.message === "NOT_OWNER") setErr("f-phone", msg(e)); else toast(msg(e)); })
      .then(function () { busy(f, false); });
  });

  route();
})();
