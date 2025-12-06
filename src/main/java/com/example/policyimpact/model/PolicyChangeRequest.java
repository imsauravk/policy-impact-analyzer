package com.example.policyimpact.model;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class PolicyChangeRequest {
    @NotNull
    private Policy oldPolicy;

    @NotNull
    private Policy newPolicy;
}