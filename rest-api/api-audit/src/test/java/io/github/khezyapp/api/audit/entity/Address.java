package io.github.khezyapp.api.audit.entity;

import io.github.khezyapp.api.audit.annotation.SensitiveData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    private String city;
    @SensitiveData // Default mask
    private String street;
}
