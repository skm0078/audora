// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/config/SpikePreset.kt
// Purpose: The named runs of the S3 test protocol, so a phone test is one tap instead of five settings.

package io.github.skm0078.audora.spike.playback.config

enum class SpikePreset(val label: String, val description: String, val config: SpikeConfig) {
    GATE(
        label = "60-min gate",
        description = "Slower than real time (RTF 1.2). Screen off for 60 minutes. The S3 exit test.",
        config = SpikeConfig(realTimeFactor = 1.2),
    ),
    GATE_BUSY_CPU(
        label = "Gate, busy CPU",
        description = "Same as the gate, but the fake engine burns CPU like a real voice. Shows OEM throttling.",
        config = SpikeConfig(realTimeFactor = 1.2, engineWork = EngineWork.BUSY),
    ),
    GAP(
        label = "Gap check",
        description = "Fast engine (RTF 0.3). 5 minutes, screen on is fine. Measures the pause between sentences.",
        config = SpikeConfig(realTimeFactor = 0.3),
    ),
    GAP_PER_STREAM(
        label = "Gap, per-stream",
        description = "Gap check with Media3's experimental per-stream progression switched on.",
        config = SpikeConfig(realTimeFactor = 0.3, perStreamProgression = true),
    ),
}
