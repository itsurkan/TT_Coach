package com.ttcoachai.work

/**
 * Pure local-disk cache eviction logic for the pose-upload cache directory
 * (PoseSessionRecorder.cacheDir), extracted from [PoseUploadQueue.evictOldCache] so it's
 * testable without touching the filesystem. Two policies, applied in order: age (anything
 * older than [maxAgeDays] goes regardless of total size), then size (if what's left still
 * exceeds [maxBytes], delete the oldest remaining entries first until under budget).
 */
object PoseCacheEviction {

    data class Entry(val path: String, val lastModifiedMs: Long, val sizeBytes: Long)

    fun entriesToEvict(
        entries: List<Entry>,
        nowMs: Long,
        maxAgeDays: Int = 7,
        maxBytes: Long = 200L * 1024 * 1024,
    ): List<String> {
        val maxAgeMs = maxAgeDays * 24L * 60 * 60 * 1000
        val (stale, fresh) = entries.partition { nowMs - it.lastModifiedMs > maxAgeMs }
        val toDelete = stale.map { it.path }.toMutableList()

        var remainingBytes = fresh.sumOf { it.sizeBytes }
        if (remainingBytes > maxBytes) {
            val oldestFirst = fresh.sortedBy { it.lastModifiedMs }
            for (entry in oldestFirst) {
                if (remainingBytes <= maxBytes) break
                toDelete.add(entry.path)
                remainingBytes -= entry.sizeBytes
            }
        }
        return toDelete
    }
}
