// script.js — professional UI + animations + robust parsing (full file)
// NOTE: update BACKEND_URL to your Spring Boot tunnel if needed.
document.addEventListener("DOMContentLoaded", () => {
  // ---------- CONFIG ----------
  // Replace this with your backend tunnel URL if frontend served from a different origin.
  // Example: "https://urban-space-sniffle-j465r79745q2p647-8080.app.github.dev"
  const FALLBACK_BACKEND = "https://urban-space-sniffle-j465r79745q2p647-8080.app.github.dev";

  // If the page is served from the same origin as backend, use same-origin.
  // Otherwise use FALLBACK_BACKEND. This prevents 405 from static host.
  const BACKEND_URL = (location.origin && location.origin.includes("5500.app.github.dev")) 
    ? FALLBACK_BACKEND 
    : location.origin;

  // ---------- DOM references ----------
  const analyzeBtn = document.getElementById("analyzeBtn");
  const clearBtn = document.getElementById("clearBtn");
  const resultsSection = document.getElementById("results");
  const premiumImpactEl = document.getElementById("premiumImpact");
  const riskImpactEl = document.getElementById("riskImpact");
  const billingImpactEl = document.getElementById("billingImpact");
  const explanationContainer = document.getElementById("explanation");
  const showRaw = document.getElementById("showRaw");
  const copyBtn = document.getElementById("copyBtn");
  const printBtn = document.getElementById("printBtn");
  const downloadReport = document.getElementById("downloadReport");
  const currentYear = document.getElementById("currentYear");

  if (currentYear) currentYear.textContent = new Date().getFullYear();

  // ---------- small helpers ----------
  const safe = v => v == null ? "" : String(v);
  const setVisible = (el, yes) => { if (!el) return; el.style.display = yes ? "block" : "none"; };
  const formatCurrency = v => "$" + Number(v || 0).toFixed(2);

  // read numeric safely
  const getNum = (id, fallback = 0) => {
    const el = document.getElementById(id);
    if (!el) return fallback;
    const v = parseFloat(el.value);
    return Number.isFinite(v) ? v : fallback;
  };

  const buildPolicyPayload = () => ({
    oldPolicy: {
      coverages: {
        liability: { limit: getNum("oldLiability", 0) },
        property: { limit: getNum("oldProperty", 0) }
      },
      ratedFactors: {
        driverAge: getNum("oldDriverAge", 0),
        locationRisk: (document.getElementById("oldLocation") || {}).value || "medium",
        vehicleValue: getNum("oldVehicleValue", 0)
      },
      billing: {}
    },
    newPolicy: {
      coverages: {
        liability: { limit: getNum("newLiability", 0) },
        property: { limit: getNum("newProperty", 0) }
      },
      ratedFactors: {
        driverAge: getNum("newDriverAge", 0),
        locationRisk: (document.getElementById("newLocation") || {}).value || "medium",
        vehicleValue: getNum("newVehicleValue", 0)
      },
      billing: {}
    }
  });

  // ---------- explanation parsing/rendering ----------
  function parseExplanation(raw) {
    try {
      if (typeof raw === "object") return raw;
      const text = String(raw || "");
      const trimmed = text.trim();
      if ((trimmed.startsWith("{") || trimmed.startsWith("["))) {
        return JSON.parse(trimmed);
      }
      return { text };
    } catch (e) {
      return { text: String(raw) };
    }
  }

  function extractAssistantText(obj) {
    try {
      if (!obj) return null;
      if (typeof obj === "string") return obj;
      if (obj.text) return obj.text;
      if (obj.choices && Array.isArray(obj.choices) && obj.choices[0]) {
        const c0 = obj.choices[0];
        if (c0.message && c0.message.content) return c0.message.content;
        if (c0.text) return c0.text;
      }
    } catch (e) { /* ignore */ }
    return null;
  }

  function renderExplanationBlock(apiExplanation) {
    explanationContainer.innerHTML = "";

    // show pretty raw JSON if user toggled it
    if (showRaw && showRaw.checked) {
      const pre = document.createElement("pre");
      pre.textContent = JSON.stringify(apiExplanation, null, 2);
      pre.className = "explanation-card";
      explanationContainer.appendChild(pre);
      return;
    }

    const assistantText = extractAssistantText(apiExplanation) || apiExplanation.text || String(apiExplanation);
    const model = apiExplanation.model || (apiExplanation?.choices?.[0]?.model) || null;
    const tokens = apiExplanation.usage ? (apiExplanation.usage.total_tokens || apiExplanation.usage.completion_tokens) : null;

    // card
    const card = document.createElement("div");
    card.className = "explanation-card";

    const h = document.createElement("h4");
    h.textContent = "Impact Explanation";
    card.appendChild(h);

    // metadata
    const meta = document.createElement("div");
    meta.style.marginBottom = "8px";
    if (model) {
      const chip = document.createElement("span");
      chip.className = "chip";
      chip.textContent = model;
      meta.appendChild(chip);
    }
    if (tokens != null) {
      const chip2 = document.createElement("span");
      chip2.className = "chip";
      chip2.textContent = `${tokens} tokens`;
      meta.appendChild(chip2);
    }
    card.appendChild(meta);

    // paragraphs
    const paragraphs = String(assistantText || "").split(/\n\s*\n/).map(p => p.trim()).filter(Boolean);
    if (paragraphs.length === 0) {
      const p = document.createElement("p");
      p.className = "explanation-paragraph";
      p.textContent = "No explanation returned.";
      card.appendChild(p);
    } else {
      paragraphs.forEach(par => {
        const p = document.createElement("p");
        p.className = "explanation-paragraph";
        p.textContent = par;
        card.appendChild(p);
      });
    }

    explanationContainer.appendChild(card);
    card.style.opacity = 0;
    requestAnimationFrame(() => { card.style.transition = "opacity .26s ease"; card.style.opacity = 1; });
  }

  // ---------- render numeric results ----------
  function renderResults(data) {
    setVisible(resultsSection, true);

    const oldPremium = data?.premiumImpact?.oldPremium ?? 0;
    const newPremium = data?.premiumImpact?.newPremium ?? 0;
    const premiumDelta = data?.premiumImpact?.delta ?? (newPremium - oldPremium);

    premiumImpactEl.innerHTML = `
      <li>Old Premium: ${formatCurrency(oldPremium)}</li>
      <li>New Premium: ${formatCurrency(newPremium)}</li>
      <li>Change: ${formatCurrency(premiumDelta)}</li>
    `;

    const oldRisk = data?.riskImpact?.oldRisk ?? "N/A";
    const newRisk = data?.riskImpact?.newRisk ?? "N/A";
    const riskDelta = data?.riskImpact?.delta ?? "N/A";

    riskImpactEl.innerHTML = `
      <li>Old Risk: ${safe(oldRisk)}</li>
      <li>New Risk: ${safe(newRisk)}</li>
      <li>Change: ${safe(riskDelta)}</li>
    `;

    const oldAnnual = data?.billingImpact?.oldAnnual ?? 0;
    const newAnnual = data?.billingImpact?.newAnnual ?? 0;
    const newMonthly = data?.billingImpact?.newMonthly ?? (newAnnual / 12);
    const billingChange = data?.billingImpact?.change ?? (newAnnual - oldAnnual);

    billingImpactEl.innerHTML = `
      <li>Old Annual: ${formatCurrency(oldAnnual)}</li>
      <li>New Annual: ${formatCurrency(newAnnual)}</li>
      <li>Monthly Payment: ${formatCurrency(newMonthly)}</li>
      <li>Change: ${formatCurrency(billingChange)}</li>
    `;

    const parsed = parseExplanation(data?.explanation ?? "No explanation returned from API.");
    renderExplanationBlock(parsed);
  }

  // ---------- UI utilities ----------
  copyBtn?.addEventListener("click", async () => {
    try {
      const summary = [
        document.querySelector(".title")?.textContent || "Policy Impact",
        "Premium: " + (premiumImpactEl.textContent || "").trim(),
        "Risk: " + (riskImpactEl.textContent || "").trim()
      ].join("\n\n");
      await navigator.clipboard.writeText(summary);
      copyBtn.textContent = "Copied!";
      setTimeout(() => (copyBtn.textContent = "Copy Summary"), 1500);
    } catch (e) {
      console.warn("Copy failed", e);
    }
  });

  printBtn?.addEventListener("click", () => window.print());

  clearBtn?.addEventListener("click", () => {
    const form = document.getElementById("policyForm");
    if (form) form.reset();
    setVisible(resultsSection, false);
    explanationContainer.innerHTML = "";
  });

  downloadReport?.addEventListener("click", () => {
    const payload = buildPolicyPayload();
    const report = {
      meta: { createdAt: new Date().toISOString() },
      request: payload,
      // results are string snapshots (simple)
      results: {
        premiumImpact: premiumImpactEl.innerText,
        riskImpact: riskImpactEl.innerText,
        billingImpact: billingImpactEl.innerText
      }
    };
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `impact-report-${Date.now()}.json`;
    document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);
  });

  // ---------- main analyze action ----------
  analyzeBtn?.addEventListener("click", async () => {
    analyzeBtn.disabled = true;
    const originalText = analyzeBtn.textContent;
    analyzeBtn.textContent = "Analyzing…";
    try {
      const payload = buildPolicyPayload();

      // Use BACKEND_URL to avoid posting to the static front-end host (405)
      const resp = await fetch(`${BACKEND_URL}/api/impact`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!resp.ok) {
        const txt = await resp.text().catch(() => "");
        throw new Error(`API error ${resp.status}: ${txt}`);
      }

      const data = await resp.json();
      renderResults(data);
      // scroll to results gently
      resultsSection.scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (err) {
      console.error("Analyze failed", err);
      setVisible(resultsSection, true);
      premiumImpactEl.innerHTML = "<li>Error calculating premium impact.</li>";
      riskImpactEl.innerHTML = "<li>Error calculating risk impact.</li>";
      billingImpactEl.innerHTML = "<li>Error calculating billing impact.</li>";
      explanationContainer.innerHTML = `<div class="explanation-card"><p class="explanation-paragraph">Error: ${safe(err.message)}</p></div>`;
    } finally {
      analyzeBtn.disabled = false;
      analyzeBtn.textContent = originalText || "Analyze Impact";
    }
  });

  // accessibility: allow Enter on form to trigger analyze
  const form = document.getElementById("policyForm");
  if (form) {
    form.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        analyzeBtn.click();
      }
    });
  }
});