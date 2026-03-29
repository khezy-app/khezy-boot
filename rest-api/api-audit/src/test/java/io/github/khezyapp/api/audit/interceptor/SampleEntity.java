package io.github.khezyapp.api.audit.interceptor;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
public class SampleEntity {
    private String name;
    private List<String> roles;

    // The interceptor checks for JPA annotations to skip relations
    @ManyToOne
    private SampleEntity parent;
}
