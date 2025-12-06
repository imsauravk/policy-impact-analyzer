package com.example.policyimpact.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Map;

@Data
@AllArgsConstructor
public class ImpactReport {
    private Map<String, Object> changesDetected;
    private Map<String, Object> premiumImpact;
    private Map<String, Object> riskImpact;
    private Map<String, Object> billingImpact;
    private String explanation;
}
