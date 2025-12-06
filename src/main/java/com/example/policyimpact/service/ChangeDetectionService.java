package com.example.policyimpact.service;

import com.example.policyimpact.model.Policy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChangeDetectionService {

    public Map<String, Object> detectChanges(Policy oldPolicy, Policy newPolicy) {
        Map<String, Object> changes = new HashMap<>();

        if (!Objects.equals(oldPolicy.getCoverages(), newPolicy.getCoverages())) {
            changes.put("coverages", diff(oldPolicy.getCoverages(), newPolicy.getCoverages()));
        }
        if (!Objects.equals(oldPolicy.getRatedFactors(), newPolicy.getRatedFactors())) {
            changes.put("ratedFactors", diff(oldPolicy.getRatedFactors(), newPolicy.getRatedFactors()));
        }
        if (!Objects.equals(oldPolicy.getBilling(), newPolicy.getBilling())) {
            changes.put("billing", diff(oldPolicy.getBilling(), newPolicy.getBilling()));
        }

        return changes;
    }

    private Map<String, Object> diff(Object oldObj, Object newObj) {
        Map<String, Object> d = new HashMap<>();
        d.put("old", oldObj);
        d.put("new", newObj);
        return d;
    }
}
