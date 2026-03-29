package io.github.khezyapp.api.audit.javers;

import org.javers.core.commit.Commit;
import org.javers.core.commit.CommitId;
import org.javers.core.json.JsonConverter;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.core.metamodel.object.GlobalId;
import org.javers.core.metamodel.type.EntityType;
import org.javers.core.metamodel.type.ManagedType;
import org.javers.repository.api.JaversRepository;
import org.javers.repository.api.QueryParams;
import org.javers.repository.api.SnapshotIdentifier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class NoopJaversRepository implements JaversRepository {

    @Override
    public List<CdoSnapshot> getStateHistory(final GlobalId globalId,
                                             final QueryParams queryParams) {
        return List.of();
    }

    @Override
    public List<CdoSnapshot> getValueObjectStateHistory(final EntityType ownerEntity,
                                                        final String path,
                                                        final QueryParams queryParams) {
        return List.of();
    }

    @Override
    public List<CdoSnapshot> getStateHistory(final Set<ManagedType> givenClasses,
                                             final QueryParams queryParams) {
        return List.of();
    }

    @Override
    public Optional<CdoSnapshot> getLatest(final GlobalId globalId) {
        return Optional.empty();
    }

    @Override
    public List<CdoSnapshot> getSnapshots(final QueryParams queryParams) {
        return List.of();
    }

    @Override
    public List<CdoSnapshot> getSnapshots(final Collection<SnapshotIdentifier> snapshotIdentifiers) {
        return List.of();
    }

    @Override
    public void persist(final Commit commit) {
    }

    @Override
    public CommitId getHeadId() {
        return null;
    }

    @Override
    public void setJsonConverter(final JsonConverter jsonConverter) {
    }

    @Override
    public void ensureSchema() {
    }
}
