package io.github.khezyapp.api.audit.entity;

import io.github.khezyapp.api.audit.annotation.SensitiveData;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplexUser {

    @Id
    private Long id;

    // 1. Primitive/Simple Property
    private String username;

    // 2. Sensitive Simple Property (Triggers direct mask)
    @SensitiveData(ignore = true)
    private String password;

    // 3. Nested Object (Triggers recursion)
    private Address address;

    // 4. List of Objects (Triggers doFlattenCollection)
    private List<ContactElement> contacts;

    // 5. Map of Objects (Triggers doFlattenMapChange)
    private Map<String, SecurityQuestion> securityProfile;
}
