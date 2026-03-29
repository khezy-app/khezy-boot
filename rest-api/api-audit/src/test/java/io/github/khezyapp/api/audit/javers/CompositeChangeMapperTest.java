package io.github.khezyapp.api.audit.javers;

import io.github.khezyapp.api.audit.annotation.SensitiveData;
import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.javers.api.ValueResolver;
import io.github.khezyapp.api.audit.javers.strategy.ContainerChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.MapChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.ReferenceChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.ValueChangeStrategy;
import io.github.khezyapp.api.audit.masker.SensitiveMaskerBuilder;
import io.github.khezyapp.api.audit.model.ChangeType;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import lombok.Builder;
import lombok.Data;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeChangeMapperTest {

    @Data
    @Builder
    static class ComplexUser {
        private String name;                // Simple
        @SensitiveData(mask = "CONFIDENTIAL")
        private String ssn;                 // Simple + Masked
        private List<String> roles;         // Collection
        private Map<String, Object> metadata; // Map
        private Address address;            // Nested Object
    }

    @Data
    @Builder
    static class Address {
        private String city;
        private String zipCode;
    }

    private CompositeChangeMapper mapper;
    private Javers javers;

    @BeforeEach
    void setUp() {
        // Setup Masker with default strategies
        final SensitiveMasker masker = SensitiveMaskerBuilder.builder().build();
        final ValueResolver resolver = new DefaultValueResolver(masker);

        mapper = new CompositeChangeMapper(resolver);

        // Register all strategies
        mapper.register(new ValueChangeStrategy());
        mapper.register(new MapChangeStrategy());
        mapper.register(new ContainerChangeStrategy());
        mapper.register(new ReferenceChangeStrategy());

        javers = JaversBuilder.javers()
                .build();
    }

    @Test
    void shouldFlattenAndMaskComplexObjectDiff() {
        // Given
        final ComplexUser oldUser = ComplexUser.builder()
                .name("Old Name")
                .ssn("123-456")
                .roles(List.of("USER"))
                .metadata(Map.of("key1", "val1"))
                .address(Address.builder().city("NY").build())
                .build();

        final ComplexUser newUser = ComplexUser.builder()
                .name("New Name")
                .ssn("999-000")
                .roles(List.of("USER", "ADMIN"))
                .metadata(Map.of("key1", "val1", "key2", "val2"))
                .address(Address.builder().city("LA").build())
                .build();

        final Diff diff = javers.compare(oldUser, newUser);

        // When
        final List<EntityFieldChange> changes = mapper.mapAll(diff);

        // Then
        // 1. Verify Simple Property Change
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.getProperty()).isEqualTo("name");
            assertThat(c.getTo()).isEqualTo("New Name");
        });

        // 2. Verify Masked Property (ValueChangeStrategy + SensitiveData annotation)
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.getProperty()).isEqualTo("ssn");
            assertThat(c.getTo()).isEqualTo("CONFIDENTIAL");
        });

        // 3. Verify Collection Change (ContainerChangeStrategy)
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.getProperty()).isEqualTo("roles[1]");
            assertThat(c.getChangeType()).isEqualTo(ChangeType.ADDED);
            assertThat(c.getTo()).isEqualTo("ADMIN");
        });

        // 4. Verify Map Change (MapChangeStrategy)
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.getProperty()).isEqualTo("metadata.key2");
            assertThat(c.getChangeType()).isEqualTo(ChangeType.ADDED);
            assertThat(c.getTo()).isEqualTo("val2");
        });

        // 5. Verify Nested Object (ValueChangeStrategy + Path Resolution)
        assertThat(changes).anySatisfy(c -> {
            assertThat(c.getProperty()).isEqualTo("address.city");
            assertThat(c.getFrom()).isEqualTo("NY");
            assertThat(c.getTo()).isEqualTo("LA");
        });
    }
}
