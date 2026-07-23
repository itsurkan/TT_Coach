# Remove shared drill from community when deleting the local drill

## Problem

In Community Drills, publishing a custom drill sets `sharedCommunityId` on the local
Room drill (`CustomDrillEntity`). The only way to unshare today is the Drills
long-press menu's "Unshare" row on the local drill. `DrillsFragment.deleteDrill()`
(`app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`) deletes only the
local Room row — so deleting a shared drill orphans its `community_drills/{docId}`
Firestore doc, and because the local row (which carried the communityId and the
Unshare menu) is gone, the public copy becomes unremovable. Users must be able to
remove their drill from the community even after deleting it locally.

## Approved solution

When deleting a local drill that is shared (`sharedCommunityId != null`), the
delete confirmation offers to also remove the public community copy in the same
action.

### 1. `ConfirmDialog` — add an optional checkbox

Files: `app/src/main/java/com/ttcoachai/ui/dialogs/ConfirmDialog.kt`,
`app/src/main/res/layout/dialog_ttc_confirm.xml`

- Add param `checkboxText: String? = null`.
- Change `onConfirm` signature from `() -> Unit` to `(checked: Boolean) -> Unit`.
- Add a `CheckBox` (id `cb_confirm_option`) to the layout, `visibility=gone`,
  checked by default; shown only when `checkboxText != null`. Style consistent
  with the existing dialog (TTC design system).
- `onConfirm` receives the checkbox's checked state (`false` when the checkbox is
  hidden).
- Update the ~2 existing call sites to the new lambda shape (they ignore the
  boolean).

### 2. `DrillsFragment.deleteDrill`

- **Not shared** (`sharedCommunityId == null`): unchanged behavior — no
  checkbox, delete local row only.
- **Shared**: show the confirm dialog with `checkboxText` = "Also remove from
  community", checkbox default **checked**. On confirm:
  - Always delete the local Room row (local delete proceeds regardless of
    community outcome — accepted decision).
  - If checked **and** signed in as the creator (FirebaseAuth current user uid
    available): attempt `communityDrillRepo.unshare(sharedCommunityId, uid)`.
    - success → toast: deleted and removed from community.
    - failure → local row still deleted; toast: deleted but couldn't remove
      from community, try again later (orphan remains — accepted tradeoff).
  - If checked but **not** signed in: local delete proceeds; toast noting
    sign-in is required to remove the public copy.
  - If unchecked: local delete only; public copy intentionally stays.

### Strings

New user-facing strings needed (EN in `values/strings.xml` + UK mirror in
`values-uk/strings.xml`), following existing `drill_*` / `community_*` naming:
checkbox label, and the two/three delete-outcome toasts. List as TODO keys for
the plan to name.

## Failure handling decision

Local delete always proceeds even if community removal fails (offline etc.).
Orphaned community doc is an accepted tradeoff; user is informed via toast.

## Out of scope

- Already-orphaned community docs from drills deleted before this ships. Room
  uses destructive migration and the app is pre-release, so no cleanup/migration
  path is built.
- No changes to `firestore.rules` (the existing `unshare` already enforces
  creator-only delete).

## Testing

App-layer UI + Firestore wiring; no new shared-KMP pure logic. The
`CommunityDrillRepository.unshare` creator guard is already covered.
Manual/instrumented verification of the delete-with-checkbox flow on device.
