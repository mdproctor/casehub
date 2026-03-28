package io.casehub.core.spi;

import io.casehub.control.Milestone;
import io.casehub.control.Stage;
import io.casehub.core.CaseFileContribution;
import io.casehub.core.CaseFileItem;
import io.casehub.core.CaseStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link CaseFileStorageProvider} using
 * {@link ConcurrentHashMap}. Thread-safe for concurrent access. All data
 * is lost on application restart.
 */
@ApplicationScoped
public class InMemoryCaseFileStorage implements CaseFileStorageProvider {

    private final Map<String, CaseFileData> caseFiles = new ConcurrentHashMap<>();
    private final Map<String, Map<String, VersionedValueImpl>> caseFileData = new ConcurrentHashMap<>();
    private final Map<String, List<CaseFileContribution>> contributions = new ConcurrentHashMap<>();
    private final Map<String, LockHandleImpl> locks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Stage>> stages = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Milestone>> milestones = new ConcurrentHashMap<>();

    @Override
    public void createCaseFile(String caseFileId, Map<String, Object> initialState) {
        CaseFileData data = new CaseFileData(caseFileId, CaseStatus.PENDING, Instant.now());
        caseFiles.put(caseFileId, data);

        Map<String, VersionedValueImpl> versionedData = new ConcurrentHashMap<>();
        initialState.forEach((key, value) -> versionedData.put(key, new VersionedValueImpl(value, 1L)));
        caseFileData.put(caseFileId, versionedData);
    }

    @Override
    public Optional<Map<String, Object>> retrieveCaseFile(String caseFileId) {
        Map<String, VersionedValueImpl> data = caseFileData.get(caseFileId);
        if (data == null) {
            return Optional.empty();
        }
        Map<String, Object> snapshot = data.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value));
        return Optional.of(snapshot);
    }

    @Override
    public void updateCaseStatus(String caseFileId, CaseStatus newStatus) {
        CaseFileData data = caseFiles.get(caseFileId);
        if (data != null) {
            data.status = newStatus;
            if (newStatus == CaseStatus.COMPLETED || newStatus == CaseStatus.FAULTED ||
                newStatus == CaseStatus.CANCELLED) {
                data.completedAt = Instant.now();
            }
        }
    }

    @Override
    public void deleteCaseFile(String caseFileId) {
        caseFiles.remove(caseFileId);
        caseFileData.remove(caseFileId);
        contributions.remove(caseFileId);
        locks.remove(caseFileId);
        stages.remove(caseFileId);
        milestones.remove(caseFileId);
    }

    @Override
    public void putKey(String caseFileId, String key, Object value) {
        caseFileData.computeIfAbsent(caseFileId, k -> new ConcurrentHashMap<>())
                .compute(key, (k, oldValue) -> {
                    long newVersion = oldValue == null ? 1L : oldValue.version + 1;
                    return new VersionedValueImpl(value, newVersion);
                });
    }

    @Override
    public Optional<Object> getKey(String caseFileId, String key) {
        Map<String, VersionedValueImpl> data = caseFileData.get(caseFileId);
        if (data == null) {
            return Optional.empty();
        }
        VersionedValueImpl versionedValue = data.get(key);
        return versionedValue == null ? Optional.empty() : Optional.of(versionedValue.value);
    }

    @Override
    public Set<String> getKeys(String caseFileId) {
        Map<String, VersionedValueImpl> data = caseFileData.get(caseFileId);
        return data == null ? Set.of() : new HashSet<>(data.keySet());
    }

    @Override
    public Map<String, Object> getSnapshot(String caseFileId) {
        return retrieveCaseFile(caseFileId).orElse(Map.of());
    }

    @Override
    public void recordContribution(String caseFileId, String taskDefinitionId, Set<String> keys, Instant timestamp) {
        CaseFileContribution contribution = new CaseFileContribution(caseFileId, taskDefinitionId, keys, timestamp);
        contributions.computeIfAbsent(caseFileId, k -> new ArrayList<>()).add(contribution);
    }

    @Override
    public List<CaseFileContribution> getContributionHistory(String caseFileId) {
        return contributions.getOrDefault(caseFileId, List.of());
    }

    @Override
    public boolean putIfVersion(String caseFileId, String key, Object value, long expectedVersion) {
        Map<String, VersionedValueImpl> data = caseFileData.get(caseFileId);
        if (data == null) {
            return false;
        }

        VersionedValueImpl current = data.get(key);
        if (current == null && expectedVersion != 0) {
            return false;
        }
        if (current != null && current.version != expectedVersion) {
            return false;
        }

        long newVersion = expectedVersion + 1;
        data.put(key, new VersionedValueImpl(value, newVersion));
        return true;
    }

    @Override
    public Optional<CaseFileItem> getVersioned(String caseFileId, String key) {
        Map<String, VersionedValueImpl> data = caseFileData.get(caseFileId);
        if (data == null) {
            return Optional.empty();
        }
        VersionedValueImpl versionedValue = data.get(key);
        if (versionedValue == null) {
            return Optional.empty();
        }
        CaseFileItem item = new CaseFileItem();
        item.setValue(versionedValue.value);
        item.setVersion(versionedValue.version);
        return Optional.of(item);
    }

    @Override
    public Optional<LockHandle> tryLock(String caseFileId, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        LockHandleImpl newLock = new LockHandleImpl(
                UUID.randomUUID().toString(),
                caseFileId,
                now,
                expiresAt
        );

        LockHandleImpl existingLock = locks.putIfAbsent(caseFileId, newLock);
        if (existingLock != null && existingLock.expiresAt.isAfter(now)) {
            return Optional.empty();
        }

        locks.put(caseFileId, newLock);
        return Optional.of(newLock);
    }

    @Override
    public boolean renewLock(LockHandle handle, Duration ttl) {
        LockHandleImpl existingLock = locks.get(handle.getCaseFileId());
        if (existingLock == null || !existingLock.lockId.equals(handle.getLockId())) {
            return false;
        }

        existingLock.expiresAt = Instant.now().plus(ttl);
        return true;
    }

    @Override
    public void releaseLock(LockHandle handle) {
        locks.remove(handle.getCaseFileId(), handle);
    }

    @Override
    public void saveStage(String caseFileId, Stage stage) {
        stages.computeIfAbsent(caseFileId, k -> new ConcurrentHashMap<>())
                .put(stage.getStageId(), stage);
    }

    @Override
    public Optional<Stage> getStage(String caseFileId, String stageId) {
        Map<String, Stage> caseStages = stages.get(caseFileId);
        if (caseStages == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(caseStages.get(stageId));
    }

    @Override
    public List<Stage> getAllStages(String caseFileId) {
        Map<String, Stage> caseStages = stages.get(caseFileId);
        if (caseStages == null) {
            return List.of();
        }
        return new ArrayList<>(caseStages.values());
    }

    @Override
    public void deleteStage(String caseFileId, String stageId) {
        Map<String, Stage> caseStages = stages.get(caseFileId);
        if (caseStages != null) {
            caseStages.remove(stageId);
        }
    }

    @Override
    public void saveMilestone(String caseFileId, Milestone milestone) {
        milestones.computeIfAbsent(caseFileId, k -> new ConcurrentHashMap<>())
                .put(milestone.getMilestoneId(), milestone);
    }

    @Override
    public Optional<Milestone> getMilestone(String caseFileId, String milestoneId) {
        Map<String, Milestone> caseMilestones = milestones.get(caseFileId);
        if (caseMilestones == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(caseMilestones.get(milestoneId));
    }

    @Override
    public List<Milestone> getAllMilestones(String caseFileId) {
        Map<String, Milestone> caseMilestones = milestones.get(caseFileId);
        if (caseMilestones == null) {
            return List.of();
        }
        return new ArrayList<>(caseMilestones.values());
    }

    @Override
    public void deleteMilestone(String caseFileId, String milestoneId) {
        Map<String, Milestone> caseMilestones = milestones.get(caseFileId);
        if (caseMilestones != null) {
            caseMilestones.remove(milestoneId);
        }
    }

    @Override
    public void cleanup(Predicate<CaseFileMetadata> shouldDelete) {
        List<String> toDelete = caseFiles.values().stream()
                .filter(shouldDelete)
                .map(CaseFileMetadata::getCaseFileId)
                .collect(Collectors.toList());
        toDelete.forEach(this::deleteCaseFile);
    }

    private static class CaseFileData implements CaseFileMetadata {
        private final String caseFileId;
        private CaseStatus status;
        private final Instant createdAt;
        private Instant completedAt;

        CaseFileData(String caseFileId, CaseStatus status, Instant createdAt) {
            this.caseFileId = caseFileId;
            this.status = status;
            this.createdAt = createdAt;
        }

        @Override
        public String getCaseFileId() {
            return caseFileId;
        }

        @Override
        public CaseStatus getStatus() {
            return status;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }

        @Override
        public Optional<Instant> getCompletedAt() {
            return Optional.ofNullable(completedAt);
        }
    }

    private static class VersionedValueImpl {
        private final Object value;
        private final long version;

        VersionedValueImpl(Object value, long version) {
            this.value = value;
            this.version = version;
        }
    }

    private static class LockHandleImpl implements LockHandle {
        private final String lockId;
        private final String caseFileId;
        private final Instant acquiredAt;
        private Instant expiresAt;

        LockHandleImpl(String lockId, String caseFileId, Instant acquiredAt, Instant expiresAt) {
            this.lockId = lockId;
            this.caseFileId = caseFileId;
            this.acquiredAt = acquiredAt;
            this.expiresAt = expiresAt;
        }

        @Override
        public String getLockId() {
            return lockId;
        }

        @Override
        public String getCaseFileId() {
            return caseFileId;
        }

        @Override
        public Instant getAcquiredAt() {
            return acquiredAt;
        }

        @Override
        public Instant getExpiresAt() {
            return expiresAt;
        }
    }
}
