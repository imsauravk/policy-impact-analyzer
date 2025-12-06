package com.example.policyimpact.model;

import lombok.Data;
import java.util.Map;

@Data
public class Policy {
    private String policyId;
    private Map<String, Coverage> coverages;
    private RatedFactors ratedFactors;
    private BillingInfo billing;
}