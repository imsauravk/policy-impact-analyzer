package com.example.policyimpact.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class BillingService {

    public Map<String, Object> recalc(double oldAnnual, double newAnnual, int term) {
        Map<String, Object> result = new HashMap<>();

        double delta = newAnnual - oldAnnual;

        result.put("oldAnnual", oldAnnual);
        result.put("newAnnual", newAnnual);
        result.put("change", delta);
        result.put("newMonthly", newAnnual / term);

        return result;
    }
}