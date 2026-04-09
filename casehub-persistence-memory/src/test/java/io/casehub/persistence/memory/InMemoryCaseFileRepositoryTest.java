package io.casehub.persistence.memory;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCaseFileRepositoryTest {

    private InMemoryCaseFileRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCaseFileRepository();
    }

    @Test
    void createReturnsUniqueIds() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile first = repository.create("type-a", Map.of(), ctx);
        CaseFile second = repository.create("type-b", Map.of(), ctx);
        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void createSetsInitialStateAndCaseType() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("doc-analysis", Map.of("doc", "hello"), ctx);
        assertEquals("doc-analysis", cf.getCaseType());
        assertEquals(Optional.of("hello"), cf.get("doc", String.class));
        assertEquals(CaseStatus.PENDING, cf.getStatus());
    }

    @Test
    void findByIdReturnsCreatedCaseFile() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile created = repository.create("type-a", Map.of(), ctx);
        Optional<CaseFile> found = repository.findById(created.getId());
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
    }

    @Test
    void findByIdReturnsEmptyForUnknown() {
        assertTrue(repository.findById(99999L).isEmpty());
    }

    @Test
    void createChildLinksParentAndChild() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile parent = repository.create("parent-type", Map.of(), ctx);
        CaseFile child = repository.createChild("child-type", Map.of(), parent);

        assertTrue(child.getParentCase().isPresent());
        assertEquals(parent.getId(), child.getParentCase().get().getId());
        assertEquals(1, parent.getChildCases().size());
        assertEquals(child.getId(), parent.getChildCases().get(0).getId());
    }

    @Test
    void createChildInheritsTraceId() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile parent = repository.create("parent-type", Map.of(), ctx);
        CaseFile child = repository.createChild("child-type", Map.of(), parent);
        assertEquals(parent.getPropagationContext().getTraceId(),
                     child.getPropagationContext().getTraceId());
    }

    @Test
    void findByStatusFiltersCorrectly() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf1 = repository.create("type-a", Map.of(), ctx);
        CaseFile cf2 = repository.create("type-b", Map.of(), ctx);
        cf1.setStatus(CaseStatus.RUNNING);

        List<CaseFile> running = repository.findByStatus(CaseStatus.RUNNING);
        assertEquals(1, running.size());
        assertEquals(cf1.getId(), running.get(0).getId());

        List<CaseFile> pending = repository.findByStatus(CaseStatus.PENDING);
        assertEquals(1, pending.size());
        assertEquals(cf2.getId(), pending.get(0).getId());
    }

    @Test
    void deleteRemovesCaseFile() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("type-a", Map.of(), ctx);
        repository.delete(cf.getId());
        assertTrue(repository.findById(cf.getId()).isEmpty());
    }
}
