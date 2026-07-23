# Remove your drill from the community, from the Community Drills screen

**Problem.** Publishing a custom drill sets `sharedCommunityId` on the local Room drill. The ONLY way to unshare was the Drills long-press "Unshare" row on the local drill. Delete the local drill and its `community_drills/{docId}` Firestore doc is orphaned — permanently unremovable, because the local row carried both the community id and the menu row. Users must be able to remove their own drill from the community.

**Superseded approach (recorded, do not resurrect).** An earlier iteration added a checked-by-default "Also remove from community" checkbox to the delete confirm dialog (commits db34976, 6f5ffcd, e667089 — all reverted). Rejected because it only helps at delete time, cannot rescue drills already orphaned, and is defeated whenever `sharedCommunityId` is lost locally.

**Approved solution.** Put removal where the public copy lives: a creator-only "Remove from community" action on the Community Drills detail sheet. It works independently of the local drill, so it also rescues already-orphaned public copies.

Components:
1. `sheet_community_drill_detail.xml` gains a `MaterialButton` `btnRemoveFromCommunity` below the existing `btnCopyToMyDrills`, `visibility="gone"`, danger styling.
2. `CommunityDrillDetailSheet` reveals that button only when the signed-in, non-anonymous user's uid equals `drill.creatorUid`. Tapping it opens the existing `ConfirmDialog` (destructive). On confirm it calls the existing creator-gated `CommunityDrillRepository.unshare(communityId, uid)`.
3. On success it also clears the local link if a local copy still exists: look the drill up by `sharedCommunityId` and save it back with `sharedCommunityId = null`, so the local drill returns to "private" and can be published again. The local drill itself is KEPT — only the link is cleared.
4. On success the sheet reports a fragment result and dismisses; `CommunityDrillsActivity` listens and calls `loadDrills()` so the browse list refreshes.
5. `CustomDrillRepository` gains a passthrough `getBySharedCommunityId` — the DAO query already exists (`CustomDrillDao.getBySharedCommunityId`), it was simply never exposed.

**Failure handling.** On unshare failure show an error toast and leave everything as-is (the public doc and the local link both survive) — the user can retry, because unlike the delete flow this action does not destroy anything first.

**Unchanged.** The Drills long-press "Unshare" row stays as-is. `ConfirmDialog` keeps its original signature (no checkbox). `firestore.rules` unchanged. Room schema unchanged.

**Known limitation (NOT fixed here).** `ExerciseEditorActivity.onPrimaryClicked()` rebuilds `CustomDrillEntity` without `sharedCommunityId`, and the DAO upsert REPLACEs, so editing a shared drill silently clears the local link. Consequence: the Drills "Unshare" row disappears, and step 3 above will not find the local drill to clear. Removal from the community screen still works (it does not depend on the local link). Logged as a follow-up.

**Testing.** App-layer UI + Firestore wiring; no new shared-KMP pure logic. Build gate `./gradlew :app:assembleDebug` plus on-device verification.
