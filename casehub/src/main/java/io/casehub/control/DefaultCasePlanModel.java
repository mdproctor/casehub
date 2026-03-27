package io.casehub.control;

import io.casehub.core.CaseFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

/**
 * In-memory MVP implementation of the {@link CasePlanModel} interface, representing the control
 * CaseHub described by Hayes-Roth (1985). Maintains a priority-ordered scheduling agenda of
 * {@link PlanItem}s, current focus of attention, strategy, resource budget, and extensible key-value
 * state. All operations are thread-safe, using a {@link PriorityBlockingQueue} for the agenda and
 * {@link ConcurrentHashMap} for state maps.
 */
public class DefaultCasePlanModel implements CasePlanModel {

    private final String casePlanModelId;
    private final CaseFile domainCaseFile;

    private final PriorityBlockingQueue<PlanItem> agenda;
    private final ConcurrentHashMap<String, Object> state;
    private final ConcurrentHashMap<String, Object> resourceBudget;

    private volatile String focus;
    private volatile String focusRationale;
    private volatile String strategy;

    public DefaultCasePlanModel(CaseFile domainCaseFile) {
        this.casePlanModelId = UUID.randomUUID().toString();
        this.domainCaseFile = Objects.requireNonNull(domainCaseFile, "domainCaseFile must not be null");
        this.agenda = new PriorityBlockingQueue<>(11); // default initial capacity
        this.state = new ConcurrentHashMap<>();
        this.resourceBudget = new ConcurrentHashMap<>();
    }

    @Override
    public String getCasePlanModelId() {
        return casePlanModelId;
    }

    @Override
    public CaseFile getDomainCaseFile() {
        return domainCaseFile;
    }

    // ---- Scheduling Agenda ----

    @Override
    public void addPlanItem(PlanItem planItem) {
        Objects.requireNonNull(planItem, "planItem must not be null");
        agenda.add(planItem);
    }

    @Override
    public void clearAgenda() {
        agenda.clear();
    }

    @Override
    public void removePlanItem(String planItemId) {
        Objects.requireNonNull(planItemId, "planItemId must not be null");
        agenda.removeIf(planItem -> planItemId.equals(planItem.getPlanItemId()));
    }

    @Override
    public List<PlanItem> getAgenda() {
        List<PlanItem> pending = agenda.stream()
                .filter(planItem -> planItem.getStatus() == PlanItem.PlanItemStatus.PENDING)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.sort(pending);
        return Collections.unmodifiableList(pending);
    }

    @Override
    public List<PlanItem> getTopPlanItems(int maxCount) {
        if (maxCount <= 0) {
            return Collections.emptyList();
        }
        List<PlanItem> pending = getAgenda();
        return Collections.unmodifiableList(
                pending.size() <= maxCount ? pending : pending.subList(0, maxCount));
    }

    // ---- Focus of Attention ----

    @Override
    public void setFocus(String focusArea) {
        this.focus = focusArea;
    }

    @Override
    public Optional<String> getFocus() {
        return Optional.ofNullable(focus);
    }

    @Override
    public void setFocusRationale(String rationale) {
        this.focusRationale = rationale;
    }

    // ---- Strategy ----

    @Override
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    @Override
    public Optional<String> getStrategy() {
        return Optional.ofNullable(strategy);
    }

    // ---- Resource Tracking ----

    @Override
    public void setResourceBudget(Map<String, Object> budget) {
        Objects.requireNonNull(budget, "budget must not be null");
        resourceBudget.clear();
        resourceBudget.putAll(budget);
    }

    @Override
    public Map<String, Object> getResourceBudget() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(resourceBudget));
    }

    // ---- General Key-Value State ----

    @Override
    public void put(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        state.put(key, value);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Object value = state.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(state));
    }
}
