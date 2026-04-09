package io.casehub.persistence.hibernate;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.spi.CaseFileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hibernate implementation of {@link CaseFileRepository} using the JPA EntityManager directly.
 * Uses PostgreSQL for production; tests override the datasource to H2.
 *
 * <p>Avoids implementing {@code PanacheRepositoryBase} to prevent method-signature conflicts
 * between Panache's {@code findById(Id) -> Entity} and the SPI's
 * {@code findById(Long) -> Optional<CaseFile>}.
 */
@ApplicationScoped
public class HibernateCaseFileRepository implements CaseFileRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public CaseFile create(String caseType, Map<String, Object> initialState,
                           PropagationContext propagationContext) {
        HibernateCaseFile cf = new HibernateCaseFile(caseType, initialState, propagationContext);
        em.persist(cf);
        return cf;
    }

    @Override
    @Transactional
    public CaseFile createChild(String caseType, Map<String, Object> initialState, CaseFile parent) {
        PropagationContext childCtx = parent.getPropagationContext().createChild();
        HibernateCaseFile child = new HibernateCaseFile(caseType, initialState, childCtx);
        HibernateCaseFile hibernateParent = (HibernateCaseFile) parent;
        child.setParentCase(hibernateParent);
        hibernateParent.addChildCase(child);
        em.persist(child);
        return child;
    }

    @Override
    @Transactional
    public Optional<CaseFile> findById(Long id) {
        return Optional.ofNullable(em.find(HibernateCaseFile.class, id));
    }

    @Override
    @Transactional
    public List<CaseFile> findByStatus(CaseStatus status) {
        return em.createQuery(
                        "SELECT cf FROM HibernateCaseFile cf WHERE cf.status = :status",
                        HibernateCaseFile.class)
                .setParameter("status", status)
                .getResultList()
                .stream()
                .map(cf -> (CaseFile) cf)
                .toList();
    }

    @Override
    @Transactional
    public void save(CaseFile caseFile) {
        if (caseFile instanceof HibernateCaseFile hcf) {
            em.merge(hcf);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        HibernateCaseFile cf = em.find(HibernateCaseFile.class, id);
        if (cf != null) {
            em.remove(cf);
        }
    }
}
