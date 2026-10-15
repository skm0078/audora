// File: app/src/main/kotlin/io/github/skm0078/audora/di/DiagnosticsModule.kt
// Purpose: Provides the :core diagnostics objects (state file, session tracker, startup check) as app singletons.

package io.github.skm0078.audora.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.skm0078.audora.core.config.DiagnosticsConfig
import io.github.skm0078.audora.core.diagnostics.DiagnosticsStateStore
import io.github.skm0078.audora.core.diagnostics.ProcessExitSource
import io.github.skm0078.audora.core.diagnostics.ReadingSessionTracker
import io.github.skm0078.audora.core.diagnostics.StartupDiagnostics
import io.github.skm0078.audora.diagnostics.AndroidProcessExitSource
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    abstract fun processExitSource(source: AndroidProcessExitSource): ProcessExitSource

    companion object {
        private const val STATE_FILE = "diagnostics/state.properties"

        @Provides
        @Singleton
        fun diagnosticsConfig(): DiagnosticsConfig = DiagnosticsConfig()

        @Provides
        @Singleton
        fun diagnosticsStateStore(@ApplicationContext context: Context): DiagnosticsStateStore =
            DiagnosticsStateStore(File(context.filesDir, STATE_FILE))

        @Provides
        @Singleton
        fun readingSessionTracker(store: DiagnosticsStateStore): ReadingSessionTracker =
            ReadingSessionTracker(store, System::currentTimeMillis)

        @Provides
        @Singleton
        fun startupDiagnostics(
            exitSource: ProcessExitSource,
            store: DiagnosticsStateStore,
            tracker: ReadingSessionTracker,
        ): StartupDiagnostics = StartupDiagnostics(exitSource, store, tracker)
    }
}
