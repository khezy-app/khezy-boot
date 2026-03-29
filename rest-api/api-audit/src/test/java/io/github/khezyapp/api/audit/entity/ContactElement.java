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
public class ContactElement {
    private String type; // e.g., "EMAIL"
    @SensitiveData(mask = "MASKED_VALUE")
    private String value;
}
