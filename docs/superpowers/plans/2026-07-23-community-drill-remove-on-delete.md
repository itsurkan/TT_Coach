# Plan: Remove community copy on local delete of a shared drill

- **Spec:** [docs/superpowers/specs/2026-07-23-community-drill-remove-on-delete-design.md](../specs/2026-07-23-community-drill-remove-on-delete-design.md)
- **Branch:** work directly on a branch off current working branch (see repo convention — never a worktree)
- **Scope:** `app/` layer only (Android UI + Firestore wiring). No shared-KMP logic, no new pure functions — TDD red/green does not apply here. Each code task is gated by `./gradlew :app:assembleDebug` compiling; a final task does manual/device verification.

## Global Constraints

- Commit with **explicit file paths only** — never `git add -A` (working tree carries unrelated artifacts).
- `shared/` is untouched — this is an app-layer-only change.
- `firestore.rules` is unchanged (out of scope per spec).
- Room schema is unchanged — no `AppDatabase` version bump. `sharedCommunityId` already exists on `CustomDrillEntity`/`Exercise`.
- Keep `values-uk/strings.xml` mirrored with `values/strings.xml` for every string added.
- Local delete always proceeds even if community removal fails (accepted orphan on failure) — never block or roll back the local delete.
- Commit messages: `feat(community-drills): ...` / `refactor(community-drills): ...` prefix, each ending with:
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  ```

## Task 1 — Add an optional checkbox to ConfirmDialog

**Files:**
- `app/src/main/res/layout/dialog_ttc_confirm.xml`
- `app/src/main/java/com/ttcoachai/ui/dialogs/ConfirmDialog.kt`

### 1a. Layout XML

Insert a `MaterialCheckBox` between the existing `tv_confirm_body` `TextView` (ends at line 49) and the button-row `LinearLayout` (starts at line 51) in `app/src/main/res/layout/dialog_ttc_confirm.xml`. Full element to insert:

```xml
    <com.google.android.material.checkbox.MaterialCheckBox
        android:id="@+id/cb_confirm_option"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="14dp"
        android:visibility="gone"
        android:fontFamily="@font/inter_tight_regular"
        android:textSize="13.5sp"
        android:textColor="@color/ttc_text_2"
        app:buttonTint="@color/ttc_gold_bright"/>
```

Resulting file (full, for reference — this is the exact end state of `dialog_ttc_confirm.xml`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="296dp"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center_horizontal"
    android:background="@drawable/bg_ttc_confirm_card"
    android:elevation="8dp"
    android:paddingTop="22dp"
    android:paddingStart="20dp"
    android:paddingEnd="20dp"
    android:paddingBottom="18dp">

    <FrameLayout
        android:layout_width="54dp"
        android:layout_height="54dp"
        android:background="@drawable/bg_ttc_confirm_badge">

        <ImageView
            android:id="@+id/iv_confirm_icon"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:layout_gravity="center"
            app:tint="@color/ttc_error"
            android:src="@drawable/ic_trash"
            android:contentDescription="@null"/>
    </FrameLayout>

    <TextView
        android:id="@+id/tv_confirm_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="15dp"
        android:fontFamily="@font/inter_tight_bold"
        android:textSize="18sp"
        android:textColor="@color/ttc_text_1"
        android:gravity="center"/>

    <TextView
        android:id="@+id/tv_confirm_body"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:fontFamily="@font/inter_tight_regular"
        android:textSize="13.5sp"
        android:lineSpacingMultiplier="1.55"
        android:textColor="@color/ttc_text_2"
        android:gravity="center"/>

    <com.google.android.material.checkbox.MaterialCheckBox
        android:id="@+id/cb_confirm_option"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="14dp"
        android:visibility="gone"
        android:fontFamily="@font/inter_tight_regular"
        android:textSize="13.5sp"
        android:textColor="@color/ttc_text_2"
        app:buttonTint="@color/ttc_gold_bright"/>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="20dp"
        android:orientation="horizontal">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_confirm_cancel"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginEnd="10dp"
            style="@style/TTC.Button.Confirm.Cancel"/>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_confirm_ok"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            style="@style/TTC.Button.Confirm.Destructive"/>
    </LinearLayout>
</LinearLayout>
```

### 1b. ConfirmDialog.kt

Replace the full contents of `app/src/main/java/com/ttcoachai/ui/dialogs/ConfirmDialog.kt` with:

```kotlin
package com.ttcoachai.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.ttcoachai.R

/**
 * Reusable centered confirm dialog (Dialog 14a). Bespoke icon-badge + button-row layout,
 * not a stock MaterialAlertDialogBuilder — see design spec for why. Parameterized so it's
 * reusable for future confirms beyond delete.
 */
object ConfirmDialog {
    fun show(
        context: Context,
        @DrawableRes iconRes: Int,
        title: String,
        body: String,
        confirmLabel: String,
        cancelLabel: String,
        destructive: Boolean = true,
        checkboxText: String? = null,
        onConfirm: (checked: Boolean) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ttc_confirm, null)
        val dialog = Dialog(context).apply {
            setContentView(view)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        view.findViewById<ImageView>(R.id.iv_confirm_icon).setImageResource(iconRes)
        view.findViewById<TextView>(R.id.tv_confirm_title).text = title
        view.findViewById<TextView>(R.id.tv_confirm_body).text = body

        val checkbox = view.findViewById<MaterialCheckBox>(R.id.cb_confirm_option)
        if (checkboxText == null) {
            checkbox.visibility = android.view.View.GONE
        } else {
            checkbox.text = checkboxText
            checkbox.visibility = android.view.View.VISIBLE
            checkbox.isChecked = true
        }

        val confirmButton = view.findViewById<MaterialButton>(R.id.btn_confirm_ok).apply {
            text = confirmLabel
        }

        view.findViewById<MaterialButton>(R.id.btn_confirm_cancel).apply {
            text = cancelLabel
            setOnClickListener { dialog.dismiss() }
        }
        confirmButton.setOnClickListener {
            dialog.dismiss()
            onConfirm(checkbox.isChecked)
        }

        dialog.show()
    }
}
```

Notes for the implementer:
- `checkbox.isChecked` reads `false` when the checkbox was left `GONE` (default unchecked state, never toggled), so the "no checkbox" call sites get `onConfirm(false)` — harmless since they ignore the argument.
- Do **not** edit `DrillsFragment.kt` in this task. There are exactly two existing callers of `ConfirmDialog.show` (`deleteDrill` ~line 357, `unshareFromCommunity` ~line 412), both passing a trailing lambda that ignores its parameter (`{ ... }` with no declared params). Changing `onConfirm` from `() -> Unit` to `(Boolean) -> Unit` does not break either call site — Kotlin still compiles a single-param trailing lambda that doesn't reference `it`. `unshareFromCommunity` needs no changes at all, in this task or later ones.

### Verify

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. No unresolved-reference or type-mismatch errors from `ConfirmDialog.kt` or `DrillsFragment.kt`.

### Commit

```
git add app/src/main/res/layout/dialog_ttc_confirm.xml app/src/main/java/com/ttcoachai/ui/dialogs/ConfirmDialog.kt
git commit -m "$(cat <<'EOF'
feat(community-drills): add optional checkbox to ConfirmDialog

Needed so the delete-drill confirm can offer removing the public
community copy in the same action, without a second dialog.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Task 2 — Add the four new strings (EN + UK)

**Files:**
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-uk/strings.xml`

Insert the four new strings immediately after the existing `drill_cancel` entry in each file (EN at line 808, UK at line 625), so they stay grouped with the other `drill_delete_*` strings.

### 2a. `app/src/main/res/values/strings.xml` — insert after line 808 (`<string name="drill_cancel">Cancel</string>`):

```xml
    <string name="drill_delete_also_community_checkbox">Also remove from community</string>
    <string name="drill_delete_and_community_toast">"%1$s" deleted and removed from community</string>
    <string name="drill_delete_community_failed_toast">Deleted, but couldn\'t remove from community. Try again later.</string>
    <string name="drill_delete_community_signin_needed">Deleted. Sign in to remove the public copy from community.</string>
```

Note: escape the apostrophe in "couldn't" as `\'` — matches Android string-resource convention and this file's existing pattern of escaping apostrophes/quotes (see `drill_delete_message` above using `\"`).

### 2b. `app/src/main/res/values-uk/strings.xml` — insert after line 625 (`<string name="drill_cancel">Скасувати</string>`):

```xml
    <string name="drill_delete_also_community_checkbox">Також видалити зі спільноти</string>
    <string name="drill_delete_and_community_toast">«%1$s» видалено та прибрано зі спільноти</string>
    <string name="drill_delete_community_failed_toast">Видалено, але не вдалося прибрати зі спільноти. Спробуйте пізніше.</string>
    <string name="drill_delete_community_signin_needed">Видалено. Увійдіть, щоб прибрати публічну копію зі спільноти.</string>
```

### Verify

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL` — this exercises `aapt2` resource compilation/linking for both locales; a malformed XML or duplicate name fails this step with an `AAPT` error pointing at the file/line.

### Commit

```
git add app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml
git commit -m "$(cat <<'EOF'
feat(community-drills): add strings for remove-from-community-on-delete

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Task 3 — Wire deleteDrill to branch on shared vs. not

**File:** `app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt`

Replace the existing `deleteDrill` function (current lines 356–374) with:

```kotlin
    private fun deleteDrill(exercise: Exercise) {
        val communityId = exercise.sharedCommunityId
        com.ttcoachai.ui.dialogs.ConfirmDialog.show(
            context = requireContext(),
            iconRes = R.drawable.ic_trash,
            title = getString(R.string.drill_delete_title),
            body = getString(R.string.drill_delete_message, exercise.name),
            confirmLabel = getString(R.string.drill_delete_confirm),
            cancelLabel = getString(R.string.drill_cancel),
            destructive = true,
            checkboxText = if (communityId != null) {
                getString(R.string.drill_delete_also_community_checkbox)
            } else {
                null
            }
        ) { removeFromCommunity ->
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) { customDrillRepo.delete(exercise.id) }
                if (_binding == null) return@launch

                if (communityId != null && removeFromCommunity) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(requireContext(),
                            getString(R.string.drill_delete_community_signin_needed), Toast.LENGTH_SHORT).show()
                    } else {
                        val result = withContext(Dispatchers.IO) { communityDrillRepo.unshare(communityId, uid) }
                        if (_binding == null) return@launch
                        if (result.isSuccess) {
                            Toast.makeText(requireContext(),
                                getString(R.string.drill_delete_and_community_toast, exercise.name), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(),
                                getString(R.string.drill_delete_community_failed_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(),
                        getString(R.string.drill_delete_toast, exercise.name), Toast.LENGTH_SHORT).show()
                }

                if (_binding == null) return@launch
                reloadCustomDrills()
            }
        }
    }
```

Notes for the implementer:
- `exercise.sharedCommunityId` is read once up front (`communityId`) so both the dialog's `checkboxText` decision and the post-delete branch use the same nullable value.
- Local delete (`customDrillRepo.delete`) always runs first and unconditionally — per spec, community-removal failure must never block or undo the local delete.
- The `_binding == null` guard is repeated after every suspend hop that could outlive the fragment view, matching the existing pattern already used in `shareToCommunity`/`unshareFromCommunity` in this file.
- `communityDrillRepo` and `FirebaseAuth` are both already imported/available in this file (`communityDrillRepo` field at line 48, `FirebaseAuth` import at line 30) — no new imports needed.
- Do not touch `unshareFromCommunity` (lines 410–436) — it already compiles unchanged against the new `ConfirmDialog.show` signature (Task 1).

### Verify

```
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

### Commit

```
git add app/src/main/java/com/ttcoachai/fragment/DrillsFragment.kt
git commit -m "$(cat <<'EOF'
feat(community-drills): offer removing public copy when deleting a shared drill

deleteDrill previously only removed the local Room row, orphaning the
community_drills Firestore doc for shared drills. Now the delete
confirm offers a checked-by-default checkbox to also unshare; local
delete always proceeds even if the community removal fails.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Task 4 — Manual device verification (no code change, no commit)

Use the `run-on-phone` skill to build/install/launch on the connected device, then walk through:

1. **Publish and delete with checkbox checked.** Drills tab → long-press a custom drill → Share to community (if not already shared) → confirm it appears in Community Drills (browse screen). Long-press the same local drill → Delete → confirm the checkbox "Also remove from community" is visible and checked by default → tap Delete. Expect: drill gone from the Drills list, toast reads "... deleted and removed from community", and the entry is gone from the Community Drills browse screen (may need a refresh/reopen).
2. **Publish and delete with checkbox unchecked.** Publish a second drill, long-press → Delete, uncheck the checkbox, tap Delete. Expect: local drill gone, toast is the plain "Deleted ..." message (no community mention), and the drill **still appears** in Community Drills.
3. **Delete a non-shared drill.** Long-press a drill that was never published → Delete. Expect: no checkbox is shown in the dialog at all (falls back to the original simple confirm), delete proceeds normally.
4. **Offline delete of a shared drill.** Enable airplane mode (or otherwise cut network), delete a shared drill with the checkbox checked. Expect: local drill disappears immediately regardless, and the toast is "Deleted, but couldn't remove from community. Try again later." Re-enable network afterward and confirm the orphaned Firestore doc still exists (accepted per spec — no auto-retry).

Use the `phone-screenshot` skill to capture the checkbox dialog state and at least one toast for the record if useful, saved to `tmp/screenshots/` per the project convention. No code changes and no commit for this task — it is a verification gate only.
