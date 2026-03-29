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
public class SecurityQuestion {
    private String question;
    @SensitiveData(mask = "********")
    private String answer;
}
