package com.example.policyimpact.model;

import lombok.Data;

@Data
public class BillingInfo {
    private String billingPlan;
    private int term;
    private double annualPremium;
}