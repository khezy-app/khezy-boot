package io.github.khezyapp.api.audit.interceptor;

import io.github.khezyapp.api.audit.api.AuditLogService;
import io.github.khezyapp.api.audit.entity.Address;
import io.github.khezyapp.api.audit.entity.ComplexUser;
import io.github.khezyapp.api.audit.entity.ContactElement;
import io.github.khezyapp.api.audit.entity.SecurityQuestion;
import io.github.khezyapp.api.audit.javers.CompositeChangeMapper;
import io.github.khezyapp.api.audit.javers.DefaultValueResolver;
import io.github.khezyapp.api.audit.javers.strategy.ContainerChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.MapChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.ReferenceChangeStrategy;
import io.github.khezyapp.api.audit.javers.strategy.ValueChangeStrategy;
import io.github.khezyapp.api.audit.masker.SensitiveMaskerBuilder;
import io.github.khezyapp.api.audit.model.AuditEntityChange;
import io.github.khezyapp.api.audit.model.AuditLogRecord;
import io.github.khezyapp.api.audit.model.ChangeType;
import io.github.khezyapp.api.audit.model.EntityFieldChange;
import lombok.Getter;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.javers.core.JaversBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KhezyAuditLogHibernateInterceptorTest {

    private AuditServiceImpl auditLogService;
    @Mock
    private Transaction transaction;

    private KhezyAuditLogHibernateInterceptor interceptor;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditServiceImpl();
        final var compositeChangeMapper = new CompositeChangeMapper(
                new DefaultValueResolver(
                        SensitiveMaskerBuilder.builder().build()
                )
        );
        compositeChangeMapper.register(new ValueChangeStrategy());
        compositeChangeMapper.register(new ContainerChangeStrategy());
        compositeChangeMapper.register(new MapChangeStrategy());
        compositeChangeMapper.register(new ReferenceChangeStrategy());
        interceptor = new KhezyAuditLogHibernateInterceptor(
                JaversBuilder.javers().build(),
                auditLogService,
                compositeChangeMapper
        );
    }

    @Test
    void testCreateNewEntity() {
        final var newUser = ComplexUser.builder()
                .id(1L)
                .username("khezy_dev")
                .password("plain_password")
                .address(Address.builder()
                        .city("Battambang")
                        .street("St 57b")
                        .build())
                .contacts(List.of(
                        new ContactElement("EMAIL", "dev@khezy.com")
                ))
                .securityProfile(Map.of(
                        "Q1", new SecurityQuestion("Pet?", "Cat")
                ))
                .build();
        // Hibernate state mapping
        final var state = new Object[] {
                newUser.getUsername(),
                newUser.getPassword(),
                newUser.getAddress(),
                newUser.getContacts(),
                newUser.getSecurityProfile()
        };
        final var propertyNames = new String[] {
                "username", "password", "address", "contacts", "securityProfile"
        };

        // Simulate invoke interceptor for create new entity
        interceptor.onPersist(newUser, null, state, propertyNames, null);
        interceptor.postFlush(List.<Object>of(newUser).iterator());
        when(transaction.getStatus()).thenReturn(TransactionStatus.ACTIVE);
        interceptor.beforeTransactionCompletion(transaction);
        interceptor.afterTransactionCompletion(transaction);

        final var entityChanges = auditLogService.getAuditEntityChanges();
        assertEquals(1, entityChanges.size());

        final var entityChange = entityChanges.get(0);
        assertEquals(ComplexUser.class.getSimpleName(), entityChange.getEntityName());
        assertEquals(1L, entityChange.getEntityId());

        final var propertyChanges = entityChanges.get(0).getChanges();
        // Password config as ignore so it should not in audit change result
        final var noPassword = !propertyChanges.isEmpty() &&
                propertyChanges.stream()
                .noneMatch(a -> a.getProperty().equals("password"));
        assertTrue(noPassword);

        assertTrue(propertyChanges.stream().allMatch(a -> a.getChangeType().isAdded()));

        // security config as mask value ********
        final var mapChanges = propertyChanges.stream()
                .collect(Collectors.toMap(EntityFieldChange::getProperty, Function.identity()));
        assertEquals("********", mapChanges.get("securityProfile.Q1.answer").getTo());
        assertEquals("khezy_dev", mapChanges.get("username").getTo());
        assertEquals("Battambang", mapChanges.get("address.city").getTo());
        assertEquals("***", mapChanges.get("address.street").getTo());
        assertEquals("EMAIL", mapChanges.get("contacts[0].type").getTo());
        assertEquals("MASKED_VALUE", mapChanges.get("contacts[0].value").getTo());
    }

    @Test
    void testRemoveEntity() {
        final var deletedUser = ComplexUser.builder()
                .id(1L)
                .username("khezy_dev")
                .password("plain_password")
                .address(Address.builder()
                        .city("Battambang")
                        .street("St 57b")
                        .build())
                .contacts(List.of(
                        new ContactElement("EMAIL", "dev@khezy.com")
                ))
                .securityProfile(Map.of(
                        "Q1", new SecurityQuestion("Pet?", "Cat")
                ))
                .build();
        // Hibernate state mapping
        final var state = new Object[] {
                deletedUser.getUsername(),
                deletedUser.getPassword(),
                deletedUser.getAddress(),
                deletedUser.getContacts(),
                deletedUser.getSecurityProfile()
        };
        final var propertyNames = new String[] {
                "username", "password", "address", "contacts", "securityProfile"
        };

        // Simulate invoke interceptor for create new entity
        interceptor.onRemove(deletedUser, deletedUser.getId(), state, propertyNames, null);
        interceptor.postFlush(List.<Object>of(deletedUser).iterator());
        when(transaction.getStatus()).thenReturn(TransactionStatus.ACTIVE);
        interceptor.beforeTransactionCompletion(transaction);
        interceptor.afterTransactionCompletion(transaction);

        final var entityChanges = auditLogService.getAuditEntityChanges();
        assertEquals(1, entityChanges.size());

        final var entityChange = entityChanges.get(0);
        assertEquals(ComplexUser.class.getSimpleName(), entityChange.getEntityName());
        assertEquals(1L, entityChange.getEntityId());

        final var propertyChanges = entityChanges.get(0).getChanges();
        // Password config as ignore so it should not in audit change result
        final var noPassword = !propertyChanges.isEmpty() &&
                propertyChanges.stream()
                        .noneMatch(a -> a.getProperty().equals("password"));
        assertTrue(noPassword);

        assertTrue(propertyChanges.stream().allMatch(a -> a.getChangeType().isRemoved()));

        // security config as mask value ********
        final var mapChanges = propertyChanges.stream()
                .collect(Collectors.toMap(EntityFieldChange::getProperty, Function.identity()));
        assertEquals("********", mapChanges.get("securityProfile.Q1.answer").getFrom());
        assertEquals("khezy_dev", mapChanges.get("username").getFrom());
        assertEquals("Battambang", mapChanges.get("address.city").getFrom());
        assertEquals("***", mapChanges.get("address.street").getFrom());
        assertEquals("EMAIL", mapChanges.get("contacts[0].type").getFrom());
        assertEquals("MASKED_VALUE", mapChanges.get("contacts[0].value").getFrom());
    }

    @Test
    void testUpdateEntity() {
        // 1. Old State (Previous)
        final var oldUser = ComplexUser.builder()
                .id(1L)
                .username("khezy_dev")
                .contacts(new ArrayList<>(List.of(
                        new ContactElement("PHONE", "081234567") // To be removed
                )))
                .securityProfile(new HashMap<>(Map.of(
                        "Q1", new SecurityQuestion("City?", "Battambang") // To be changed
                )))
                .build();

        // 2. New State (Current)
        final var currentUser = ComplexUser.builder()
                .id(oldUser.getId())
                .username("khezy_updated") // Changed primitive
                .contacts(new ArrayList<>(List.of(
                        new ContactElement("EMAIL", "new@khezy.com") // Added element
                )))
                .securityProfile(new HashMap<>(Map.of(
                        "Q1", new SecurityQuestion("City?", "Phnom Penh") // Changed value
                )))
                .build();

        // Hibernate state arrays
        final var currentState = new Object[] {
                currentUser.getUsername(), currentUser.getPassword(), currentUser.getAddress(),
                currentUser.getContacts(), currentUser.getSecurityProfile()
        };
        final var previousState = new Object[] {
                oldUser.getUsername(), oldUser.getPassword(), oldUser.getAddress(),
                oldUser.getContacts(), oldUser.getSecurityProfile()
        };
        final var propertyNames = new String[] {
                "username", "password", "address", "contacts", "securityProfile"
        };

        // Simulate event calls by hibernate
        interceptor.onFlushDirty(currentUser, (Object) currentUser.getId(),
                currentState, previousState, propertyNames, null);
        interceptor.postFlush(List.<Object>of(currentUser).iterator());
        when(transaction.getStatus()).thenReturn(TransactionStatus.ACTIVE);
        interceptor.beforeTransactionCompletion(transaction);
        interceptor.afterTransactionCompletion(transaction);

        final var entityChanges = auditLogService.getAuditEntityChanges();
        assertEquals(1, entityChanges.size());

        final var entityChange = entityChanges.get(0);
        assertEquals(ComplexUser.class.getSimpleName(), entityChange.getEntityName());
        assertEquals(1L, entityChange.getEntityId());

        final var propertyChanges = entityChange.getChanges();
        final var mapChanges = propertyChanges.stream()
                .collect(Collectors.toMap(EntityFieldChange::getProperty, Function.identity()));

        assertEquals(oldUser.getUsername(), mapChanges.get("username").getFrom());
        assertEquals(currentUser.getUsername(), mapChanges.get("username").getTo());
        assertEquals(ChangeType.VALUE_CHANGES, mapChanges.get("username").getChangeType());

        // flatten property for contact
        assertEquals(oldUser.getContacts().get(0).getType(), mapChanges.get("contacts[0].type").getFrom());
        assertEquals(currentUser.getContacts().get(0).getType(), mapChanges.get("contacts[0].type").getTo());
        assertEquals(ChangeType.VALUE_CHANGES, mapChanges.get("contacts[0].type").getChangeType());

        assertEquals("MASKED_VALUE", mapChanges.get("contacts[0].value").getFrom());
        assertEquals("MASKED_VALUE", mapChanges.get("contacts[0].value").getTo());
        assertEquals(ChangeType.VALUE_CHANGES, mapChanges.get("contacts[0].value").getChangeType());

        assertEquals("********", mapChanges.get("securityProfile.Q1.answer").getFrom());
        assertEquals("********", mapChanges.get("securityProfile.Q1.answer").getTo());
        assertEquals(ChangeType.VALUE_CHANGES, mapChanges.get("securityProfile.Q1.answer").getChangeType());

        // Assert property not change not capture in audit
        assertFalse(mapChanges.containsKey("password"));
        assertFalse(mapChanges.containsKey("address"));
        assertFalse(mapChanges.containsKey("securityProfile.Q1.question"));
    }

    public static class AuditServiceImpl implements AuditLogService {

        @Getter
        private final List<AuditEntityChange> auditEntityChanges = new ArrayList<>();

        @Override
        public void onRequest(final AuditLogRecord<?> auditLogRecord) {
        }

        @Override
        public void onAuditEntityChanges(final AuditEntityChange auditEntityChange) {
            auditEntityChanges.add(auditEntityChange);
        }
    }
}
