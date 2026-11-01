package app.unrot

import android.app.Application

class UnrotApp : Application() {
    /**
     * The single dependency container (ADR-011). Activities and, later, the accessibility
     * service reach it via `(applicationContext as UnrotApp).container`.
     * `by lazy` uses synchronized initialization, so it is thread-safe.
     */
    val container: AppContainer by lazy { AppContainer(this) }
}
