# Community Drills — Implementation Plan

## Context

New feature: a public collection of user-created drills. Users explicitly share their custom drills ("Share to community" on long-press), others browse them, rate 1–5 stars, sort by rating / creator name / newest, search by name, preview settings, and copy a drill into their own local drills to edit and train. Entry point: a card on the Drills tab.

Decisions confirmed with the user:
- **Publish**: explicit share, snapshot (later local edits don't update the public copy). Creator can **unshare/delete** only — no in-place edit.
- **Rating**: 1–5 stars, one per signed-in user (re-rating updates), card shows average + count.
- **Auth**: Google sign-in required to publish/rate; anonymous users can browse + copy.
- **Browse v1**: sort by rating / newest / creator, client-side text search, preview bottom sheet before copy.

Architecture: direct Firestore from the client (no backend/Cloud Functions, consistent with repo's zero-backend state). New top-level collection `community_drills/{docId}` + `ratings/{uid}` subcollection. Rating aggregates (`ratingSum`/`ratingCount`) maintained by a client Firestore transaction. Sorting/search done client-side over a bounded fetch (`orderBy sharedAtMs desc, limit 200`) — avoids composite indexes and derived-field ordering.

## Risks (accepted for v1)

1. **Destructive Room migration**: `AppDatabase` is v7 with `fallbackToDestructiveMigration()`; bumping to v8 (new column) **wipes ALL local data** (sessions, baselines, custom drills) on upgrade. Consistent with existing convention, but user-facing consequence.
2. **Client-side aggregate trust**: a malicious client could corrupt `ratingSum/Count`; rules constrain field set and star range only. Full integrity needs Cloud Functions — out of scope.
3. **`firestore.rules` is documentation-as-code** until manually deployed (no firebase.json/CLI config in repo).

## Data model

- `CustomDrillEntity` + `sharedCommunityId: String?` (null = private; set = linked public doc). Room v7→v8.
- `CommunityDrill` (Firestore model): name, baseTemplate, focusCsv, referenceType, strictnessX, perPhaseTargetsJson, creatorUid, creatorName, creatorPhotoUrl, sharedAtMs, ratingSum, ratingCount; computed `averageRating`. `DrillRating(stars, ratedAtMs)` in `ratings/{uid}`.
- **Never travels to cloud**: `drillType`, `baselineId` (local Room PK). A copied drill gets fresh `custom_<timestamp>` id, `baselineId = null`, `sharedCommunityId = null`.

## Tasks (execute via superpowers:subagent-driven-development, sonnet subagents, TDD where noted)

### 1. Room schema
- Modify [CustomDrillEntity.kt](app/src/main/java/com/ttcoachai/models/CustomDrillEntity.kt): add `sharedCommunityId: String? = null`.
- Modify [AppDatabase.kt](app/src/main/java/com/ttcoachai/db/AppDatabase.kt): version 7→8.
- Optional DAO lookup `getBySharedCommunityId` in [CustomDrillDao.kt](app/src/main/java/com/ttcoachai/db/CustomDrillDao.kt).
- Verify: `./gradlew :app:compileDebugKotlin`.

### 2. Models + mapper (TDD, JVM tests)
- Create `app/src/main/java/com/ttcoachai/models/CommunityDrill.kt` (`COLLECTION = "community_drills"`), `DrillRating`.
- Create `app/src/main/java/com/ttcoachai/models/CommunityDrillMapper.kt` — pure functions, no Firebase types:
  - `fromCustomDrill(entity, creatorUid, creatorName, creatorPhotoUrl, nowMs)` — ratingSum/Count = 0; drops drillType/baselineId.
  - `toCustomDrillEntity(drill, newDrillType, nowMs)` — baselineId = null, sharedCommunityId = null.
  - `CommunityDrill.toMap()` for Firestore writes.
- Test `app/src/test/java/com/ttcoachai/models/CommunityDrillMapperTest.kt`: field copying, no local-field leaks, toMap key set.

### 3. Sort/search/rating-math helpers (TDD, JVM tests)
- Create `app/src/main/java/com/ttcoachai/util/CommunityDrillSort.kt`: `enum CommunitySortMode { RATING, NEWEST, CREATOR }`; `sort()` (rating desc, tie-break by count; newest by sharedAtMs; creator case-insensitive alpha), `search()` (blank→all, case-insensitive substring on name).
- Create `app/src/main/java/com/ttcoachai/util/RatingAggregate.kt`: `applyRating(currentSum, currentCount, previousStars?, newStars)` — first rating increments count; re-rate adjusts sum only; require stars 1..5.
- Tests: `CommunityDrillSortTest.kt`, `RatingAggregateTest.kt`.

### 4. CommunityDrillRepository (Firestore)
- Create `app/src/main/java/com/ttcoachai/repository/CommunityDrillRepository.kt`, mirroring [TrainingRepository.kt](app/src/main/java/com/ttcoachai/repository/TrainingRepository.kt) conventions (`Result<T>` suspend, default-injected `FirebaseFirestore`, `.await()`):
  - `publish(drill): Result<String>` (rejects anonymous callers defensively before rules do)
  - `unshare(communityId, creatorUid): Result<Unit>`
  - `fetchAll(): Result<List<CommunityDrill>>` — `orderBy("sharedAtMs", DESC).limit(200)`; set `id` from `doc.id` (manual mapping, not `toObject`)
  - `fetchOne(communityId)`, `myRating(communityId, uid): Result<DrillRating?>`
  - `rate(communityId, uid, stars)` — `runTransaction`: read own rating doc + drill doc → `RatingAggregate.applyRating` → update drill aggregates + set rating doc.
- Not unit-testable without emulator (none in repo) — logic correctness covered by Tasks 2–3; manual smoke in verification.

### 5. Publish / unshare in DrillsFragment menu
- Modify `dialog_drill_menu.xml`: add share/unshare rows.
- Modify [DrillActions.kt](app/src/main/java/com/ttcoachai/fragment/DrillActions.kt): `canShareToCommunity(exercise, isShared, isSignedInNonAnon)` (custom && !shared && signed-in), `canUnshare(exercise, isShared)`.
- Extend `Exercise` with `sharedCommunityId: String?` populated in `toExercise()` (avoids async hop at menu-open).
- [DrillsFragment.kt](app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt): `shareToCommunity` (auth check → mapper → `publish` → save entity with docId → reload → toast; anonymous → "Sign in with Google to share" dialog), `unshareFromCommunity` (confirm dialog → `unshare` → clear local link → reload).

### 6. Community browse screen
- Create `app/src/main/java/com/ttcoachai/ui/CommunityDrillsActivity.kt` + `adapter/CommunityDrillAdapter.kt`; layouts `activity_community_drills.xml`, `item_community_drill.xml`. Register in AndroidManifest (parent = host activity of DrillsFragment; verify with grep).
- UI: toolbar, search field, `ChipGroup` sort chips (`TTC.Chip.Filter`), RecyclerView of `TTC.Card` rows (name, creator + avatar, avg stars + count, relative time), loading/empty states.
- Fetch once → cache → sort/search client-side via Task 3 helpers. Row click → detail sheet.

### 7. Detail bottom sheet (preview + copy + rate)
- Create `app/src/main/java/com/ttcoachai/ui/dialogs/CommunityDrillDetailSheet.kt` + `sheet_community_drill_detail.xml`, following `SessionSummarySheet.kt` pattern (`BottomSheetDialogFragment`, `ThemeOverlay_TTC_BottomSheet`, `newInstance(id)`).
- Content: creator, focus areas (decode focusCsv), base template, strictness, per-phase targets preview via existing `PerPhaseTargetsCodec`, avg rating + count, interactive 1–5 star input (disabled for anonymous → "Sign in to rate"), "Copy to my drills" always enabled.
- On open: `fetchOne` + `myRating` (preselect stars). Rate → `rate()` → refetch to refresh average.

### 8. Copy-to-my-drills flow
- Small helper `app/src/main/java/com/ttcoachai/util/CommunityDrillCopier.kt`: `copyToLocal(drill, customDrillRepo, nowMs)` → fresh `custom_<nowMs>` id via mapper → `customDrillRepo.save`.
- Sheet's Copy button → helper → toast "Added to My Drills" → dismiss; drill appears via DrillsFragment's existing onResume reload.

### 9. Entry point on Drills page
- Modify `fragment_drills.xml`: community card (`TTC.Card`) near "ALL PROGRAMS" header (reuse an existing drawable icon if suitable).
- DrillsFragment: click → `startActivity(CommunityDrillsActivity)`.

### 10. Strings EN + UK
- Add to both `values/strings.xml` and `values-uk/strings.xml` as each UI task lands; final sweep for key-set parity. Keys: `drill_share_community`, `drill_unshare_community`, `drill_unshare_confirm_*`, `community_sign_in_required_*`, `drills_community_entry_*`, `community_title`, `community_search_hint`, `community_sort_{rating,newest,creator}`, `community_empty_state`, `community_rating_count_format`, `community_copy_button`, `community_copy_success_toast`, `community_rate_hint`, `community_sign_in_to_rate`.

### 11. firestore.rules (new file, repo root)
- Public read on `community_drills`; create only signed-in non-anonymous (`sign_in_provider != 'anonymous'`) with `creatorUid == auth.uid` and zero initial aggregates; delete only by creator; update restricted to `['ratingSum','ratingCount']` via `diff().affectedKeys().hasOnly(...)`; `ratings/{uid}` writable only by owner with `stars` int 1..5.
- Header comment: manual deploy required (`firebase deploy --only firestore:rules` or console paste). Author before Task 5 ships publish.

## Execution order

1 ∥ 2 ∥ 3 (independent) → 4 → 11 → 5 → 6 → 7 → 8; 9 after 6; 10 tracks UI tasks + final parity sweep.

Per repo convention: execute via `superpowers:subagent-driven-development` (sonnet subagents, fresh per task, review between tasks); commit per logical change with explicit paths; first commit should also drop a copy of this plan into `docs/superpowers/plans/2026-07-22-community-drills.md`.

## Verification

- **Unit (JVM)**: `./gradlew :app:testDebugUnitTest --tests "*CommunityDrill*" --tests "*RatingAggregate*"` — mapper field integrity, sort/search behavior, rating math (first-rate vs re-rate).
- **Build**: `./gradlew :app:assembleDebug`.
- **Strings parity**: diff of `name="..."` key sets between EN and UK strings files.
- **On-device smoke** (via `run-on-phone` + `phone-screenshot` skills): share a custom drill (menu row appears only for custom+unshared+signed-in) → open community card on Drills tab → list loads, sort chips reorder, search filters → open detail sheet, rate (star persists on reopen; average/count update), copy → drill appears in My Drills, editable and trainable → unshare removes it from the community list.
- **Rules**: paste into Firebase console; manually confirm anonymous user cannot publish/rate but can read.
