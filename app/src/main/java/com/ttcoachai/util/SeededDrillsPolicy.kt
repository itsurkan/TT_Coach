package com.ttcoachai.util

import com.ttcoachai.models.CustomDrillEntity
import com.ttcoachai.repository.CustomDrillRepository

/**
 * Seeds two editable CustomDrillEntity rows on first run (and after any destructive-migration
 * DB wipe) so the app ships with trainable forehand drills instead of the old hardcoded
 * unlocked Exercise entries (docs/superpowers/specs/2026-07-27-no-calibration-shipped-baseline-design.md
 * §2). Pure decision + entity-construction logic; the Android-specific bits (flag storage,
 * localized name resolution) live at the call site (TTCoachApplication.onCreate()).
 */
object SeededDrillsPolicy {

    const val SEED_ANDRII_ID = "custom_seed_forehand_andrii"
    const val SEED_GENERAL_ID = "custom_seed_forehand_general"

    data class SeedNames(val andriiName: String, val generalName: String)

    /**
     * Seeding runs when EITHER the "has ever seeded" flag is unset (first-ever launch) OR
     * [existingDrillCount] is 0 (a destructive-migration DB wipe clears Room's custom_drills
     * table but not SharedPreferences, so the flag alone would never re-seed after a wipe).
     * KNOWN LIMITATION (docs/DESIGN_LIMITATIONS.md L-39): this can't distinguish "wiped by
     * migration" from "player deliberately deleted every custom drill" — the latter also
     * re-triggers seeding on next launch.
     */
    fun shouldSeed(flagAlreadySet: Boolean, existingDrillCount: Int): Boolean =
        !flagAlreadySet || existingDrillCount == 0

    /**
     * Both seed rows: `referenceType = "standard"` (no calibration gate — the whole point of
     * this spec) and the SAME [perPhaseTargetsJson] (encoded `ShippedBaselines.defaultBands()`,
     * per Task B/C) — General differs ONLY by [SEED_GENERAL_ID]'s `movementProfile = "general"`
     * (Task G/H reads this to widen the locomotion gate). `baseTemplate` is self-referential
     * (own drillType), matching how every other NEW-mode custom drill is created
     * (ExerciseEditorActivity.onPrimaryClicked, EditorMode.NEW branch).
     */
    fun buildSeedEntities(
        names: SeedNames,
        nowMs: Long,
        perPhaseTargetsJson: String
    ): List<CustomDrillEntity> = listOf(
        CustomDrillEntity(
            drillType = SEED_ANDRII_ID,
            name = names.andriiName,
            baseTemplate = SEED_ANDRII_ID,
            createdAtMs = nowMs,
            referenceType = "standard",
            perPhaseTargetsJson = perPhaseTargetsJson,
        ),
        CustomDrillEntity(
            drillType = SEED_GENERAL_ID,
            name = names.generalName,
            baseTemplate = SEED_GENERAL_ID,
            createdAtMs = nowMs,
            referenceType = "standard",
            perPhaseTargetsJson = perPhaseTargetsJson,
            movementProfile = "general",
        ),
    )

    /**
     * Idempotent insert: writes only the [entities] whose `drillType` has no existing row —
     * `CustomDrillDao.upsert` is `OnConflictStrategy.REPLACE`, so calling it unconditionally
     * would silently clobber a player's edits (rename, re-banded targets) on every app start.
     */
    suspend fun seedMissing(repo: CustomDrillRepository, entities: List<CustomDrillEntity>) {
        for (entity in entities) {
            if (repo.get(entity.drillType) == null) {
                repo.save(entity)
            }
        }
    }
}
