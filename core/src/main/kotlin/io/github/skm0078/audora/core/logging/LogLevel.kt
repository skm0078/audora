// File: core/src/main/kotlin/io/github/skm0078/audora/core/logging/LogLevel.kt
// Purpose: Severity of a log entry, with the one-letter code used in log files.

package io.github.skm0078.audora.core.logging

enum class LogLevel(val code: Char) {
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E'),
}
