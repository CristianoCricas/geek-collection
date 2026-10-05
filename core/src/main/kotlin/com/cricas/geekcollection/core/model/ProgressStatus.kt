package com.cricas.geekcollection.core.model

/** Progress status shown next to the completion bar. Mutually exclusive. */
enum class ProgressStatus {
    IN_PROGRESS,
    PAUSED,
    ABANDONED,
    /** "História finalizada" for video games, "Finalizado" otherwise. */
    FINISHED;

    companion object {
        fun fromName(name: String?): ProgressStatus =
            entries.firstOrNull { it.name == name } ?: IN_PROGRESS
    }
}
