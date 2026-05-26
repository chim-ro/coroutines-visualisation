package com.coroutines.viz.scenario

object ScenarioRegistry {
    private val scenarios: Map<String, Scenario> = listOf(
        HappyPathScenario(),
        SuspensionResumptionScenario(),
        DownwardCancellationScenario(),
        ChildExceptionScenario(),
        ScopeComparisonScenario(),
        NestedScopesScenario(),
        WithTimeoutScenario(),
        CoroutineContextScenario(),
        DispatchersScenario(),
        ThreadsVsCoroutinesScenario(),
        NonCancellableContextScenario(),
        CooperativeCancellationScenario(),
        InvokeOnCompletionScenario(),
        LazyStartScenario(),
        JobFactoryTrapScenario(),
        AsyncPropagationContrastScenario(),
        CancelledScopeTrapScenario(),
        ExternalScopeScenario(),
        CoroutineExceptionHandlerScenario()
    ).associateBy { it.info.id }

    fun listAll(): List<ScenarioInfo> = scenarios.values.map { it.info }

    fun getById(id: String): Scenario? = scenarios[id]
}
