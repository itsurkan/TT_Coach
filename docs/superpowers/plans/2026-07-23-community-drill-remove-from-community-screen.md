# Remove-from-community-screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a drill's creator remove their published drill from the Community Drills detail sheet, independently of the local drill.

**Architecture:** Reveal a creator-only danger button on the existing detail bottom sheet; confirm via the existing ConfirmDialog; call the existing creator-gated CommunityDrillRepository.unshare; clear the local sharedCommunityId link if a local copy remains; report a fragment result so the browse list reloads.

**Tech Stack:** Kotlin, Android Material Components, Firebase Auth + Firestore, Room, ViewBinding, coroutines.

## Global Constraints

- Commit with explicit file paths only — never `git add -A`.
- Never run git checkout/restore/reset/stash — the working tree carries another session's in-flight edits to `DrillsFragment.kt` and `fragment_drills.xml`. Do not touch those two files.
- `shared/` KMP module untouched — app layer only.
- Room schema unchanged; no `AppDatabase` version bump. `CustomDrillDao.getBySharedCommunityId` already exists.
- `ConfirmDialog.show` keeps its existing signature — do not add parameters.
- Every EN string added to `values/strings.xml` needs its UK counterpart in `values-uk/strings.xml`, with matching placeholders.
- Build gate is `./gradlew :app:assembleDebug`. Do NOT run `./gradlew test` (pre-existing unrelated failure in `MotionAnalyzerJsonTest`).
- Commit messages use `feat(community-drills):` prefix and end with:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

## Task 1 — Expose getBySharedCommunityId + add strings

- [ ] Files: `app/src/main/java/com/ttcoachai/repository/CustomDrillRepository.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-uk/strings.xml`.

The repository currently reads:

```kotlin
package com.ttcoachai.repository

import com.ttcoachai.db.CustomDrillDao
import com.ttcoachai.models.CustomDrillEntity

class CustomDrillRepository(private val dao: CustomDrillDao) {

    suspend fun save(entity: CustomDrillEntity) = dao.upsert(entity)

    suspend fun getAll(): List<CustomDrillEntity> = dao.getAll()

    suspend fun get(drillType: String): CustomDrillEntity? = dao.getByDrillType(drillType)

    suspend fun count(): Int = dao.count()

    suspend fun delete(drillType: String) = dao.deleteByDrillType(drillType)
}
```

Add one more one-line passthrough member, matching this exact style, anywhere among the existing ones (e.g. directly after `get`):

```kotlin
    suspend fun getBySharedCommunityId(communityId: String): CustomDrillEntity? = dao.getBySharedCommunityId(communityId)
```

The DAO method `CustomDrillDao.getBySharedCommunityId(communityId: String): CustomDrillEntity?` already exists — do not add it to the DAO, only expose it through the repository.

**Strings.** In `app/src/main/res/values/strings.xml`, anchor by the existing line:

```xml
    <string name="community_copy_success_toast">Added to My Drills</string>
```

and add the following six new strings directly after it (do not anchor by line number — the file may have shifted):

```xml
    <string name="community_remove_button">Remove from community</string>
    <string name="community_remove_confirm_title">Remove from community?</string>
    <string name="community_remove_confirm_message">\"%1$s\" will no longer be public. Your own copy stays in your drills.</string>
    <string name="community_remove_confirm_button">Remove</string>
    <string name="community_remove_success_toast">Removed from community</string>
    <string name="community_remove_error_toast">Couldn\'t remove from community. Try again.</string>
```

In `app/src/main/res/values-uk/strings.xml`, anchor by the existing line:

```xml
    <string name="community_copy_success_toast">Додано до моїх вправ</string>
```

and add the Ukrainian mirror directly after it:

```xml
    <string name="community_remove_button">Прибрати зі спільноти</string>
    <string name="community_remove_confirm_title">Прибрати зі спільноти?</string>
    <string name="community_remove_confirm_message">«%1$s» більше не буде публічним. Ваша власна копія залишиться у ваших вправах.</string>
    <string name="community_remove_confirm_button">Прибрати</string>
    <string name="community_remove_success_toast">Прибрано зі спільноти</string>
    <string name="community_remove_error_toast">Не вдалося прибрати зі спільноти. Спробуйте ще раз.</string>
```

Note the `%1$s` placeholder must be present verbatim in both variants (Room/Android string-format validation fails the build otherwise).

**Build gate:**

```
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` — this task only adds a repository passthrough and string resources, no behavior change yet.

**Commit:**

```
git add app/src/main/java/com/ttcoachai/repository/CustomDrillRepository.kt app/src/main/res/values/strings.xml app/src/main/res/values-uk/strings.xml
git commit -m "$(cat <<'EOF'
feat(community-drills): expose getBySharedCommunityId + add remove-from-community strings

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Task 2 — Add the button to the sheet layout

- [ ] File: `app/src/main/res/layout/sheet_community_drill_detail.xml`.

The last element in the layout is currently:

```xml
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnCopyToMyDrills"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="20dp"
        android:text="@string/community_copy_button"
        style="@style/TTC.Button.Primary"/>
```

Add immediately after it, before the closing `</LinearLayout>`:

```xml
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnRemoveFromCommunity"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:visibility="gone"
        android:text="@string/community_remove_button"
        style="@style/TTC.Button.Danger"/>
```

`TTC.Button.Danger` exists in `app/src/main/res/values/styles.xml` (parented on `Widget.Material3.Button`) — use it as written; no substitution needed.

**Build gate:**

```
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` — new button id `btnRemoveFromCommunity` generates in `SheetCommunityDrillDetailBinding`; nothing references it yet so no compile errors.

**Commit:**

```
git add app/src/main/res/layout/sheet_community_drill_detail.xml
git commit -m "$(cat <<'EOF'
feat(community-drills): add remove-from-community button to detail sheet layout

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Task 3 — Wire the sheet

- [ ] File: `app/src/main/java/com/ttcoachai/ui/dialogs/CommunityDrillDetailSheet.kt`.

Current relevant structure (verified in the repo as of this plan):

```kotlin
class CommunityDrillDetailSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "CommunityDrillDetailSheet"
        private const val ARG_ID = "community_id"

        fun newInstance(communityId: String) = CommunityDrillDetailSheet().apply {
            arguments = Bundle().apply { putString(ARG_ID, communityId) }
        }
    }

    private var _binding: SheetCommunityDrillDetailBinding? = null
    private val binding get() = _binding!!

    private val repo by lazy { CommunityDrillRepository() }
    private val customDrillRepo by lazy {
        CustomDrillRepository(AppDatabase.getDatabase(requireContext()).customDrillDao())
    }

    private var loaded: CommunityDrill? = null

    override fun getTheme() = R.style.ThemeOverlay_TTC_BottomSheet

    override fun onCreateView(...): View { ... }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val communityId = requireArguments().getString(ARG_ID).orEmpty()

        binding.btnCopyToMyDrills.setOnClickListener { copyToMyDrills() }

        viewLifecycleOwner.lifecycleScope.launch {
            val drill = repo.fetchOne(communityId).getOrNull()
            if (_binding == null) return@launch
            if (drill == null) {
                Toast.makeText(requireContext(), R.string.community_load_error, Toast.LENGTH_SHORT).show()
                dismiss()
                return@launch
            }
            loaded = drill
            bind(drill)
            setupRatingInput(communityId)

            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && !user.isAnonymous) {
                val myRating = repo.myRating(communityId, user.uid).getOrNull()
                if (_binding != null && myRating != null) {
                    binding.rbDetailRate.rating = myRating.stars.toFloat()
                }
            }
        }
    }
    ...
    private fun copyToMyDrills() {
        val drill = loaded ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                CommunityDrillCopier.copyToLocal(drill, customDrillRepo, System.currentTimeMillis())
            }
            if (_binding == null) return@launch
            Toast.makeText(requireContext(), R.string.community_copy_success_toast, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

Existing imports at the top of the file: `android.os.Bundle`, `android.view.LayoutInflater`, `android.view.View`, `android.view.ViewGroup`, `android.widget.Toast`, `androidx.lifecycle.lifecycleScope`, `coil.load`, `coil.transform.CircleCropTransformation`, `com.google.android.material.bottomsheet.BottomSheetDialogFragment`, `com.google.firebase.auth.FirebaseAuth`, `com.ttcoachai.R`, `com.ttcoachai.databinding.SheetCommunityDrillDetailBinding`, `com.ttcoachai.db.AppDatabase`, `com.ttcoachai.models.CommunityDrill`, `com.ttcoachai.repository.CommunityDrillRepository`, `com.ttcoachai.repository.CustomDrillRepository`, `com.ttcoachai.ui.FOCUS_ORDER`, `com.ttcoachai.ui.parseFocusCsv`, `com.ttcoachai.util.CommunityDrillCopier`, `com.ttcoachai.util.PerPhaseTargetsCodec`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.withContext`.

`android.view.View` is already imported. `androidx.fragment.app.setFragmentResult` is NOT yet imported — add it.

`CommunityDrill.creatorUid: String` already exists on the model — use it directly, no lookup needed.

Make these changes:

**(a)** In the `companion object`, add a new result-key constant next to `TAG`/`ARG_ID`:

```kotlin
    companion object {
        const val TAG = "CommunityDrillDetailSheet"
        const val RESULT_REMOVED = "community_drill_removed"
        private const val ARG_ID = "community_id"

        fun newInstance(communityId: String) = CommunityDrillDetailSheet().apply {
            arguments = Bundle().apply { putString(ARG_ID, communityId) }
        }
    }
```

**(b)** Add the import:

```kotlin
import androidx.fragment.app.setFragmentResult
```

**(c)** In `onViewCreated`, inside the existing coroutine, extend the `val user = FirebaseAuth.getInstance().currentUser` block (the one that currently only prefills `myRating`) to also reveal the remove button for the creator. Reuse the same `user` val — do not add a second `FirebaseAuth.getInstance().currentUser` call:

```kotlin
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && !user.isAnonymous) {
                val myRating = repo.myRating(communityId, user.uid).getOrNull()
                if (_binding != null && myRating != null) {
                    binding.rbDetailRate.rating = myRating.stars.toFloat()
                }
                if (_binding != null && user.uid == drill.creatorUid) {
                    binding.btnRemoveFromCommunity.visibility = View.VISIBLE
                    binding.btnRemoveFromCommunity.setOnClickListener {
                        confirmRemove(communityId, user.uid, drill.name)
                    }
                }
            }
```

This preserves the existing `_binding == null` guard semantics (re-checked after the `repo.myRating` suspend hop) and adds one more guarded block after it, still inside the same `if (user != null && !user.isAnonymous)` branch — a non-anonymous signed-in user is a precondition for both the rating prefill and the creator check, so nesting here (rather than duplicating the `user != null` check) is correct and matches the file's existing style.

**(d)** Add this new private method (place it near `copyToMyDrills`, e.g. directly after it):

```kotlin
    private fun confirmRemove(communityId: String, uid: String, drillName: String) {
        com.ttcoachai.ui.dialogs.ConfirmDialog.show(
            context = requireContext(),
            iconRes = R.drawable.ic_trash,
            title = getString(R.string.community_remove_confirm_title),
            body = getString(R.string.community_remove_confirm_message, drillName),
            confirmLabel = getString(R.string.community_remove_confirm_button),
            cancelLabel = getString(R.string.drill_cancel),
            destructive = true
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = repo.unshare(communityId, uid)
                if (_binding == null) return@launch
                if (result.isSuccess) {
                    val local = withContext(Dispatchers.IO) {
                        customDrillRepo.getBySharedCommunityId(communityId)
                    }
                    if (local != null) {
                        withContext(Dispatchers.IO) {
                            customDrillRepo.save(local.copy(sharedCommunityId = null))
                        }
                    }
                    if (_binding == null) return@launch
                    Toast.makeText(requireContext(), R.string.community_remove_success_toast, Toast.LENGTH_SHORT).show()
                    setFragmentResult(RESULT_REMOVED, Bundle())
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), R.string.community_remove_error_toast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
```

Note: `customDrillRepo` calls are wrapped in `withContext(Dispatchers.IO)`, mirroring exactly how `copyToMyDrills()` wraps its `CommunityDrillCopier.copyToLocal` call in `withContext(Dispatchers.IO)` in this same file. `ConfirmDialog.show`'s `context` parameter is `requireContext()` — the sheet is alive when the button is clickable, so this is safe the same way the rest of the file already assumes an attached fragment inside click handlers.

**Full method signature reference for `ConfirmDialog.show`** (do not change this file, only call it as shown above):

```kotlin
fun show(
    context: Context,
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean = true,
    onConfirm: () -> Unit
)
```

**Full signature reference for `CommunityDrillRepository.unshare`** (already exists, do not change):

```kotlin
suspend fun unshare(communityId: String, creatorUid: String): Result<Unit>
```

**Build gate:**

```
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

**Commit:**

```
git add app/src/main/java/com/ttcoachai/ui/dialogs/CommunityDrillDetailSheet.kt
git commit -m "$(cat <<'EOF'
feat(community-drills): wire creator-only remove-from-community action in detail sheet

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Task 4 — Refresh the browse list

- [ ] File: `app/src/main/java/com/ttcoachai/ui/CommunityDrillsActivity.kt`.

The activity currently has, in `onCreate`:

```kotlin
        binding.btnBack.setOnClickListener { finish() }

        adapter = CommunityDrillAdapter(emptyList()) { drill -> openDetail(drill) }
        binding.rvCommunity.layoutManager = LinearLayoutManager(this)
        binding.rvCommunity.adapter = adapter

        binding.etCommunitySearch.doAfterTextChanged {
            currentQuery = it?.toString().orEmpty()
            applyFilter()
        }

        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentSort = when (checkedId) {
                R.id.chipSortNewest -> CommunitySortMode.NEWEST
                R.id.chipSortCreator -> CommunitySortMode.CREATOR
                else -> CommunitySortMode.RATING
            }
            applyFilter()
        }

        loadDrills()
    }

    private fun loadDrills() { ... }
```

and:

```kotlin
    private fun openDetail(drill: CommunityDrill) {
        com.ttcoachai.ui.dialogs.CommunityDrillDetailSheet
            .newInstance(drill.id)
            .show(supportFragmentManager, com.ttcoachai.ui.dialogs.CommunityDrillDetailSheet.TAG)
    }
```

Add the fragment-result listener registration right after the `adapter`/`rvCommunity` setup and before `loadDrills()` at the end of `onCreate` (registering before the first `loadDrills()` call is not required for correctness — the listener only matters once the sheet is opened later — but placing it here keeps all one-time `onCreate` wiring together):

```kotlin
        supportFragmentManager.setFragmentResultListener(
            com.ttcoachai.ui.dialogs.CommunityDrillDetailSheet.RESULT_REMOVED, this
        ) { _, _ -> loadDrills() }

        loadDrills()
    }
```

`FragmentManager.setFragmentResultListener` is a member method on `androidx.fragment.app.FragmentManager` (this activity already has `supportFragmentManager` in scope, and other code in this file already fully-qualifies `com.ttcoachai.ui.dialogs.CommunityDrillDetailSheet` rather than importing it) — it needs no additional import beyond what `androidx.fragment.app.FragmentActivity`/`BaseActivity` already brings in. Confirm this compiles as written; no import line needs to be added for `setFragmentResultListener` itself.

**Build gate:**

```
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

**Commit:**

```
git add app/src/main/java/com/ttcoachai/ui/CommunityDrillsActivity.kt
git commit -m "$(cat <<'EOF'
feat(community-drills): reload browse list after a drill is removed from community

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

## Task 5 — On-device verification (no code, no commit)

- [ ] Checklist:
  1. Open Community Drills, tap a drill you published → "Remove from community" button visible; tap it, confirm → sheet dismisses, toast, drill gone from the list.
  2. The local drill is still in your Drills, and its long-press menu now shows "Share to community" instead of "Unshare" (link cleared).
  3. Open a drill published by someone else → no Remove button.
  4. Open a drill while signed out / as guest → no Remove button.
  5. Delete a local shared drill, then open Community Drills → its public copy is still listed and can be removed there (the orphan-rescue case that motivated this design).

Note the device may be locked and require the user to unlock it.
