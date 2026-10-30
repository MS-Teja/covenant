package io.github.msteja.covenant

import android.app.Application
import io.github.msteja.covenant.core.model.Commitments
import io.github.msteja.covenant.core.rules.EffectiveRules
import io.github.msteja.covenant.data.AppCatalog
import io.github.msteja.covenant.data.StateStore
import io.github.msteja.covenant.guard.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** The few long-lived objects every screen and service shares. */
object Graph {
    lateinit var app: Application
        private set
    lateinit var store: StateStore
        private set
    lateinit var catalog: AppCatalog
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val zone: ZoneId get() = ZoneId.systemDefault()
    fun now(): Instant = Instant.now()

    private var cachedFor: Pair<Commitments, ZoneId>? = null
    private var cachedRules: EffectiveRules? = null

    /** What's in force: the covenant plus any pending changes, enforced together (R22). */
    fun rules(commitments: Commitments = store.current.commitments): EffectiveRules {
        val key = commitments to zone
        cachedRules?.takeIf { cachedFor == key }?.let { return it }
        return EffectiveRules(commitments, zone).also {
            cachedFor = key
            cachedRules = it
        }
    }

    fun init(application: Application) {
        app = application
        store = StateStore(application, scope)
        catalog = AppCatalog(application)
    }
}

class CovenantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // Re-plan the next wake-up whenever the rules or held messages change.
        Graph.scope.launch {
            Graph.store.state.filterNotNull()
                .distinctUntilChanged { a, b -> a.commitments == b.commitments && a.held == b.held && a.activity == b.activity }
                .collect { Scheduler.plan(this@CovenantApp, it) }
        }
    }
}
