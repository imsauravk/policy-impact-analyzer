package com.example.policyimpact.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.example.policyimpact.model.*;
import com.example.policyimpact.service.*;

/**
 * ImpactController - now uses ExplanationService to generate dynamic explanations
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/impact")
public class ImpactController {

    @Autowired private ChangeDetectionService changeService;
    @Autowired private PremiumService premiumService;
    @Autowired private RiskService riskService;
    @Autowired private BillingService billingService;
    @Autowired private ExplanationService explanationService; // <-- wired

    @GetMapping("/hello")
    public String hello() {
        return "Hello, world!";
    }

    @PostMapping
    public ImpactReport analyze(@RequestBody PolicyChangeRequest req) {

        Map<String, Object> changes = changeService.detectChanges(
                req.getOldPolicy(), req.getNewPolicy());

        double oldPremium = premiumService.calculatePremium(req.getOldPolicy());
        double newPremium = premiumService.calculatePremium(req.getNewPolicy());

        Map<String, Object> premiumImpact = Map.of(
                "oldPremium", oldPremium,
                "newPremium", newPremium,
                "delta", newPremium - oldPremium,
                // additional key expected by ExplanationService
                "premiumDelta", newPremium - oldPremium
        );

        double oldRisk = riskService.calculateRisk(req.getOldPolicy());
        double newRisk = riskService.calculateRisk(req.getNewPolicy());

        Map<String, Object> riskImpact = Map.of(
                "oldRisk", oldRisk,
                "newRisk", newRisk,
                "delta", newRisk - oldRisk,
                // additional key expected by ExplanationService
                "riskDelta", newRisk - oldRisk
        );

        Map<String, Object> billingImpact =
                billingService.recalc(oldPremium, newPremium, 12);

        String explanation;
        try {
            // Call ExplanationService to generate a dynamic explanation using OpenAI
            explanation = explanationService.explain(changes, premiumImpact, riskImpact, billingImpact);
        } catch (Exception e) {
            // Fail gracefully: return a helpful fallback explanation and log error
            explanation = "Explanation service unavailable. Reason: " + e.getMessage();
            // (In production logging: logger.error("Explanation failed", e));
        }

        return new ImpactReport(
                changes, premiumImpact, riskImpact, billingImpact, explanation
        );
    }
}