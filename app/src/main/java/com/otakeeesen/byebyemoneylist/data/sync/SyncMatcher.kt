package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan

interface SyncMatcher<Local, Server> {
    fun buildPlan(
        localItems: List<Local>,
        serverItems: List<Server>
    ): SyncPlan<Local, Server>
}
