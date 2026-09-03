package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan

/**
 * Orchestrates the per-group sync executions in the required order
 * (Categories → Stores → Products). Each entry is a suspend execution
 * supplied by the caller in the intended order.
 */
class SyncCoordinator(
    private val executions: List<suspend () -> Result<Boolean>>
) {
    /**
     * Runs every group execution in order and returns their individual results.
     * A failure in one group does not prevent later groups from running.
     */
    suspend fun executeAll(): List<Result<Boolean>> {
        return executions.map { it() }
    }
}
