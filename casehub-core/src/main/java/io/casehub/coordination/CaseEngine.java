package io.casehub.coordination;

import io.casehub.control.CasePlanModel;
import io.casehub.control.PlanningStrategy;
import io.casehub.control.PlanningStrategy.ControlActivationCondition;
import io.casehub.control.DefaultCasePlanModel;
import io.casehub.control.PlanItem;
import io.casehub.control.PlanItem.PlanItemStatus;
import io.casehub.core.ListenerEvaluator;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.DefaultCaseFile;
import io.casehub.core.TaskDefinition;
import io.casehub.core.TaskDefinitionRegistry;
import io.casehub.error.CaseCreationException;
import io.casehub.resilience.PoisonPillDetector;
import io.casehub.worker.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The execution engine and scheduler for the CaseHub control loop. Creates and manages domain
 * CaseFiles paired with CasePlanModels, then runs the control loop: detect CaseFile changes,
 * create PlanItems via the {@link ListenerEvaluator}, invoke {@link PlanningStrategy}s, execute
 * the top-priority PlanItems, and repeat until the CaseFile is complete, quiescent, or timed out.
 * Also handles child CaseFile creation with {@link PropagationContext} inheritance, hierarchical
 * cancellation, and lineage queries. See section 5.1.
 */
@ApplicationScoped
public class CaseEngine {

    private static final Logger LOG = Logger.getLogger(CaseEngine.class);

    @Inject
    TaskDefinitionRegistry taskDefRegistry;

    @Inject
    ListenerEvaluator listenerEvaluator;

    @Inject
    LineageService lineageService;

    @Inject
    NotificationService notificationService;

    @Inject
    PoisonPillDetector poisonPillDetector;

    private final Map<Long, CaseFile> activeCaseFiles = new ConcurrentHashMap<>();
    private final Map<Long, CasePlanModel> casePlanModels = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<CaseFile>> caseFileFutures = new ConcurrentHashMap<>();
    private final ExecutorService controlLoopExecutor = Executors.newCachedThreadPool();

    public CaseFile createAndSolve(String caseType, Map<String, Object> initialState)
            throws CaseCreationException {
        PropagationContext context = PropagationContext.createRoot();
        return createAndSolveInternal(caseType, initialState, context);
    }

    public CaseFile createAndSolve(String caseType, Map<String, Object> initialState,
                                 Duration timeout) throws CaseCreationException {
        PropagationContext context = PropagationContext.createRoot(Map.of(), timeout);
        return createAndSolveInternal(caseType, initialState, context);
    }

    private CaseFile createAndSolveInternal(String caseType, Map<String, Object> initialState,
                                          PropagationContext context) {
        DefaultCaseFile caseFile = new DefaultCaseFile(caseType, initialState, context);
        Long caseFileId = caseFile.getId();
        DefaultCasePlanModel casePlanModel = new DefaultCasePlanModel(caseFile);

        activeCaseFiles.put(caseFileId, caseFile);
        casePlanModels.put(caseFileId, casePlanModel);

        CompletableFuture<CaseFile> future = new CompletableFuture<>();
        caseFileFutures.put(caseFileId, future);

        controlLoopExecutor.submit(() -> runControlLoop(caseFile));

        return caseFile;
    }

    private void runControlLoop(CaseFile caseFile) {
        Long caseFileId = caseFile.getId();
        CasePlanModel casePlanModel = casePlanModels.get(caseFileId);

        caseFile.setStatus(CaseStatus.RUNNING);

        String caseType = caseFile.getCaseType();
        List<TaskDefinition> taskDefs = taskDefRegistry.getForCaseType(caseType);
        List<PlanningStrategy> strategies = taskDefRegistry.getStrategiesForCaseType(caseType);

        // Initial evaluation
        List<PlanItem> newPlanItems = listenerEvaluator.evaluateAndCreatePlanItems(caseFile, casePlanModel, taskDefs, null);

        while (caseFile.getStatus() == CaseStatus.RUNNING) {
            // Invoke PlanningStrategies based on activation conditions
            for (PlanningStrategy strategy : strategies) {
                if (strategy.getActivationCondition() == ControlActivationCondition.ON_NEW_PLAN_ITEMS
                        && !newPlanItems.isEmpty()) {
                    strategy.reason(casePlanModel, caseFile);
                } else if (strategy.getActivationCondition() == ControlActivationCondition.ALWAYS) {
                    strategy.reason(casePlanModel, caseFile);
                }
            }

            // Get top PlanItem to execute
            List<PlanItem> topPlanItems = casePlanModel.getTopPlanItems(1);

            if (topPlanItems.isEmpty()) {
                if (listenerEvaluator.isQuiescent(casePlanModel)) {
                    caseFile.setStatus(CaseStatus.WAITING);
                    break;
                }
            }

            newPlanItems = List.of(); // Reset for next iteration

            for (PlanItem planItem : topPlanItems) {
                planItem.setStatus(PlanItemStatus.RUNNING);

                String tdId = planItem.getTaskDefinitionId();
                var tdOpt = taskDefRegistry.getById(tdId);
                if (tdOpt.isEmpty()) {
                    planItem.setStatus(PlanItemStatus.FAULTED);
                    casePlanModel.removePlanItem(planItem.getPlanItemId());
                    LOG.warnf("TaskDefinition not found for PlanItem: tdId=%s", tdId);
                    continue;
                }

                TaskDefinition td = tdOpt.get();
                try {
                    td.execute(caseFile);
                    planItem.setStatus(PlanItemStatus.COMPLETED);
                    casePlanModel.removePlanItem(planItem.getPlanItemId());

                    // Re-evaluate entry criteria with the trigger key
                    newPlanItems = listenerEvaluator.evaluateAndCreatePlanItems(
                            caseFile, casePlanModel, taskDefs, planItem.getTriggerKey());
                } catch (Exception e) {
                    planItem.setStatus(PlanItemStatus.FAULTED);
                    LOG.errorf(e, "TaskDefinition %s failed during execution", tdId);
                    continue;
                }

                // Check if CaseFile was completed or faulted during execution
                CaseStatus currentStatus = caseFile.getStatus();
                if (currentStatus == CaseStatus.COMPLETED || currentStatus == CaseStatus.FAULTED) {
                    break;
                }
            }

            // Invoke PlanningStrategies that react to completion
            for (PlanningStrategy strategy : strategies) {
                if (strategy.getActivationCondition() == ControlActivationCondition.ON_TASK_COMPLETION) {
                    strategy.reason(casePlanModel, caseFile);
                }
            }
        }

        // Complete the future
        CompletableFuture<CaseFile> future = caseFileFutures.get(caseFileId);
        if (future != null) {
            future.complete(caseFile);
        }
    }

    public CaseFile awaitCompletion(CaseFile caseFile, Duration timeout)
            throws InterruptedException, TimeoutException {
        CompletableFuture<CaseFile> future = caseFileFutures.get(caseFile.getId());
        if (future == null) {
            return caseFile;
        }
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("CaseFile execution failed", e.getCause());
        }
        return caseFile;
    }

    public CaseFile createChildCaseFile(CaseFile parentCaseFile, String caseType,
                                        Map<String, Object> initialState) {
        PropagationContext childContext = parentCaseFile.getPropagationContext().createChild();
        return createAndSolveInternal(caseType, initialState, childContext);
    }

    public boolean cancel(CaseFile caseFile) {
        caseFile.setStatus(CaseStatus.CANCELLED);

        CasePlanModel casePlanModel = casePlanModels.get(caseFile.getId());
        if (casePlanModel != null) {
            casePlanModel.clearAgenda();
        }

        CompletableFuture<CaseFile> future = caseFileFutures.get(caseFile.getId());
        if (future != null) {
            future.complete(caseFile);
        }

        return true;
    }

    public Collection<CaseFile> getActiveCaseFiles() {
        return activeCaseFiles.values();
    }

    public Map<String, Object> getSnapshot(CaseFile caseFile) {
        return caseFile.snapshot();
    }

    public CasePlanModel getCasePlanModel(CaseFile caseFile) {
        return casePlanModels.get(caseFile.getId());
    }

    public LineageTree getLineage(CaseFile caseFile) {
        return lineageService.getFullTree(caseFile.getPropagationContext().getTraceId());
    }
}
