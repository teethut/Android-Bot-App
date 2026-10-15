package com.teethut.twiprobot.domain.model

/**
 * Dynamic cyber-cat drives & internal emotional needs state machine.
 *
 * @property affection 0.0 (neglected/needy) to 1.0 (deeply loving/purring)
 * @property energy 0.0 (exhausted/asleep) to 1.0 (hyperactive/zoomies)
 * @property curiosity 0.0 (aloof/bored) to 1.0 (eager to explore/investigate)
 */
data class FelineDrives(
    val affection: Float = 0.70f,
    val energy: Float = 0.85f,
    val curiosity: Float = 0.80f
)
