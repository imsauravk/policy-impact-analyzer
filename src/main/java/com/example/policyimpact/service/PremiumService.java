package com.example.policyimpact.service;

import com.example.policyimpact.model.Coverage;
import com.example.policyimpact.model.Policy;
import com.example.policyimpact.model.RatedFactors;
import com.example.policyimpact.util.JsonLoader;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PremiumService {

    private final Map<String, Object> rating = JsonLoader.loadJson("rating.json");

    public double calculatePremium(Policy policy) {

        Coverage liability = policy.getCoverages().get("liability");
        Coverage property = policy.getCoverages().get("property");
        RatedFactors f = policy.getRatedFactors();

        double liabilityRate = (double) rating.get("liability_rate_per_10k");
        double propertyRate = (double) rating.get("property_rate_per_1k");

        double liabPremium = (liability.getLimit() / 10000) * liabilityRate;
        double propPremium = (property.getLimit() / 1000) * propertyRate;

        Map<String, Double> loc = (Map<String, Double>) rating.get("location_multipliers");
        double locMult = loc.get(f.getLocationRisk());

        double youngDriverFactor = 1.0;
        if (f.getDriverAge() < 25) {
            youngDriverFactor += (double) rating.get("young_driver_surcharge");
        }

        return (liabPremium + propPremium) * locMult * youngDriverFactor;
    }
}
