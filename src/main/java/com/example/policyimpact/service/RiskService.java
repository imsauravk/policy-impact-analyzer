package com.example.policyimpact.service;

import com.example.policyimpact.model.Policy;
import com.example.policyimpact.model.RatedFactors;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RiskService {

    public double calculateRisk(Policy policy) {
        RatedFactors f = policy.getRatedFactors();

        Map<String, Integer> locScores = Map.of(
                "low", 10,
                "medium", 20,
                "high", 30
        );

        return (100 - f.getDriverAge())
                + (f.getVehicleValue() / 1000)
                + locScores.get(f.getLocationRisk());
    }
}