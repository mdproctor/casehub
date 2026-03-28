package io.casehub.core.spi;

import io.casehub.control.Milestone;
import io.casehub.control.Stage;
import io.casehub.core.CaseStatus;
import io.casehub.core.CaseFileContribution;
import io.casehub.core.CaseFileItem;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/**
 * SPI for persisting CaseFile state, covering key-value data operations, CaseFile lifecycle
 * management, provenance tracking, and consistency extensions such as versioned writes
 * and distributed locking. The MVP implementation is in-memory using
 * {@link java.util.concurrent.ConcurrentHashMap}. Future implementations will target
 * Redis and PostgreSQL.
 *
 * @see <a href="doc/spec.md#3.4">Spec section 3.4</a>
 */
public interface CaseFileStorageProvider {
    // CaseFile CRUD
    void createCaseFile(String caseFileId, Map<String, Object> initialState);
    Optional<Map<String, Object>> retrieveCaseFile(String caseFileId);
    void updateCaseStatus(String caseFileId, CaseStatus newStatus);
    void deleteCaseFile(String caseFileId);

    // Key-value operations
    void putKey(String caseFileId, String key, Object value);
    Optional<Object> getKey(String caseFileId, String key);
    Set<String> getKeys(String caseFileId);
    Map<String, Object> getSnapshot(String caseFileId);

    // Provenance tracking
    void recordContribution(String caseFileId, String taskDefinitionId, Set<String> keys, Instant timestamp);
    List<CaseFileContribution> getContributionHistory(String caseFileId);

    // Consistency extensions
    boolean putIfVersion(String caseFileId, String key, Object value, long expectedVersion);
    Optional<CaseFileItem> getVersioned(String caseFileId, String key);
    Optional<LockHandle> tryLock(String caseFileId, Duration ttl);
    boolean renewLock(LockHandle handle, Duration ttl);
    void releaseLock(LockHandle handle);

    // Stage persistence
    void saveStage(String caseFileId, Stage stage);
    Optional<Stage> getStage(String caseFileId, String stageId);
    List<Stage> getAllStages(String caseFileId);
    void deleteStage(String caseFileId, String stageId);

    // Milestone persistence
    void saveMilestone(String caseFileId, Milestone milestone);
    Optional<Milestone> getMilestone(String caseFileId, String milestoneId);
    List<Milestone> getAllMilestones(String caseFileId);
    void deleteMilestone(String caseFileId, String milestoneId);

    // Lifecycle
    void cleanup(Predicate<CaseFileMetadata> shouldDelete);

    /**
     * Read-only view of a CaseFile's metadata, used by lifecycle cleanup predicates
     * to decide which CaseFiles should be deleted.
     */
    interface CaseFileMetadata {
        String getCaseFileId();
        CaseStatus getStatus();
        Instant getCreatedAt();
        Optional<Instant> getCompletedAt();
    }

    /**
     * Handle representing an acquired distributed lock on a CaseFile. Callers use this
     * handle to renew or release the lock. The handle tracks acquisition and expiry times.
     */
    interface LockHandle {
        String getLockId();
        String getCaseFileId();
        Instant getAcquiredAt();
        Instant getExpiresAt();
    }
}
