package io.casehub.persistence.hibernate;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.spi.CaseFileRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class HibernateCaseFileRepositoryTest {

    @Inject
    CaseFileRepository repository;

    @Test
    @Transactional
    void createAssignsDatabaseId() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("doc-analysis", Map.of("key", "value"), ctx);
        assertNotNull(cf.getId());
        assertTrue(cf.getId() > 0);
    }

    @Test
    @Transactional
    void findByIdReturnsSavedEntity() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile created = repository.create("doc-analysis", Map.of(), ctx);
        Long id = created.getId();

        Optional<CaseFile> found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("doc-analysis", found.get().getCaseType());
    }

    @Test
    @Transactional
    void workspaceDataRoundTrips() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("doc-analysis", Map.of("input", "hello"), ctx);
        cf.put("result", "world");
        repository.save(cf);

        Optional<CaseFile> found = repository.findById(cf.getId());
        assertTrue(found.isPresent());
        assertEquals(Optional.of("hello"), found.get().get("input", String.class));
        assertEquals(Optional.of("world"), found.get().get("result", String.class));
    }

    @Test
    @Transactional
    void createChildLinksParentAndChild() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile parent = repository.create("parent-type", Map.of(), ctx);
        CaseFile child = repository.createChild("child-type", Map.of(), parent);

        assertTrue(child.getParentCase().isPresent());
        assertEquals(parent.getId(), child.getParentCase().get().getId());
    }

    @Test
    @Transactional
    void createChildInheritsTraceId() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile parent = repository.create("parent-type", Map.of(), ctx);
        CaseFile child = repository.createChild("child-type", Map.of(), parent);
        assertEquals(parent.getPropagationContext().getTraceId(),
                child.getPropagationContext().getTraceId());
    }

    @Test
    @Transactional
    void findByStatusFiltersCorrectly() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("type-a", Map.of(), ctx);
        cf.setStatus(CaseStatus.RUNNING);
        repository.save(cf);

        List<CaseFile> running = repository.findByStatus(CaseStatus.RUNNING);
        assertFalse(running.isEmpty());
        assertTrue(running.stream().anyMatch(c -> c.getId().equals(cf.getId())));
    }
}
