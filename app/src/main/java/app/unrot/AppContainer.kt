package app.unrot

import android.content.Context

/**
 * Manual dependency injection (ADR-011). Intentionally empty in Step 00; later steps add the
 * database, repositories, rules store, settings and clock here as `by lazy` properties.
 */
class AppContainer(private val appContext: Context)
