package io.casehub.core;

import io.casehub.control.CasePlanModel;
import io.casehub.control.PlanItem;
import io.casehub.control.PlanItem.PlanItemStatus;
import io.casehub.resilience.PoisonPillDetector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Evaluates {@link TaskDefinition} entry criteria against the current {@link CaseFile} state and
 * creates {@link PlanItem}s on the {@link CasePlanModel}'s scheduling agenda.
 * Also detects quiescence -- when no further TaskDefinition activations are possible.
 */
@ApplicationScoped
public class ListenerEvaluator {

    private static final Set<PlanItemStatus> ACTIVE_STATUSES =
            Set.of(PlanItemStatus.PENDING, PlanItemStatus.RUNNING, PlanItemStatus.WAITING);

    @Inject
    PoisonPillDetector poisonPillDetector;

    public List<PlanItem> evaluateAndCreatePlanItems(CaseFile domainCaseFile, CasePlanModel casePlanModel,
                                                    List<TaskDefinition> registered,
                                                    String triggerKey) {
        List<PlanItem> created = new ArrayList<>();
        List<PlanItem> agenda = casePlanModel.getAgenda();

        for (TaskDefinition td : registered) {
            // Skip quarantined TaskDefinition
            if (poisonPillDetector.isQuarantined(td.getId())) {
                continue;
            }

            // Check if all entry criteria are present on the domain CaseFile
            boolean allKeysPresent = true;
            for (String key : td.entryCriteria()) {
                if (!domainCaseFile.contains(key)) {
                    allKeysPresent = false;
                    break;
                }
            }
            if (!allKeysPresent) {
                continue;
            }

            // Check custom activation condition
            if (!td.canActivate(domainCaseFile)) {
                continue;
            }

            // Skip TaskDefinition that already has an active PlanItem on the agenda
            boolean hasActivePlanItem = agenda.stream()
                    .anyMatch(planItem -> planItem.getTaskDefinitionId().equals(td.getId())
                            && ACTIVE_STATUSES.contains(planItem.getStatus()));
            if (hasActivePlanItem) {
                continue;
            }

            // Create and add a new PlanItem
            PlanItem planItem = PlanItem.create(td, triggerKey);
            casePlanModel.addPlanItem(planItem);
            created.add(planItem);
        }

        return created;
    }

    public boolean isQuiescent(CasePlanModel casePlanModel) {
        return casePlanModel.getAgenda().stream()
                .noneMatch(planItem -> ACTIVE_STATUSES.contains(planItem.getStatus()));
    }
}
