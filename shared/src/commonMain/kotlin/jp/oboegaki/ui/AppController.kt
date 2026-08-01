package jp.oboegaki.ui

import jp.oboegaki.core.data.BuiltInThemes
import jp.oboegaki.core.data.ItemRepository
import jp.oboegaki.core.domain.MoveDecision
import jp.oboegaki.core.domain.SplitValidation
import jp.oboegaki.core.domain.ThemeValidation
import jp.oboegaki.core.model.AllSections
import jp.oboegaki.core.model.AppItem
import jp.oboegaki.core.model.AppSettings
import jp.oboegaki.core.model.ItemKind
import jp.oboegaki.core.model.ThemeDefinition
import jp.oboegaki.core.model.ItemRelation
import jp.oboegaki.platform.CalendarEventDraft
import jp.oboegaki.platform.CalendarExportResult
import jp.oboegaki.platform.CalendarExporter
import jp.oboegaki.platform.NoOpCalendarExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class MainTab { TODOS, MEMOS, ALL }

sealed interface AppOverlay {
    data class Add(val defaultKind: ItemKind) : AppOverlay
    data class Edit(val itemId: String) : AppOverlay
    data class Split(val itemId: String) : AppOverlay
    data object Settings : AppOverlay
    data object Themes : AppOverlay
    data class ThemeEditor(val theme: ThemeDefinition) : AppOverlay
    data object DataTools : AppOverlay
    data class OperationGuide(val firstLaunch: Boolean) : AppOverlay
}

data class UndoNotice(val message: String)

class AppController(
    private val repository: ItemRepository,
    private val scope: CoroutineScope,
    private val calendarExporter: CalendarExporter = NoOpCalendarExporter,
) {
    private val _sections = MutableStateFlow(AllSections())
    val sections: StateFlow<AllSections> = _sections
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings
    private val _themes = MutableStateFlow(BuiltInThemes.all)
    val themes: StateFlow<List<ThemeDefinition>> = _themes
    private val _relations = MutableStateFlow<List<ItemRelation>>(emptyList())
    val relations: StateFlow<List<ItemRelation>> = _relations
    private val _tab = MutableStateFlow(MainTab.TODOS)
    val tab: StateFlow<MainTab> = _tab
    private val _overlay = MutableStateFlow<AppOverlay?>(null)
    val overlay: StateFlow<AppOverlay?> = _overlay
    private val _todoIndex = MutableStateFlow(0)
    val todoIndex: StateFlow<Int> = _todoIndex
    private val _memoIndex = MutableStateFlow(0)
    val memoIndex: StateFlow<Int> = _memoIndex
    private val _undo = MutableStateFlow<UndoNotice?>(null)
    val undo: StateFlow<UndoNotice?> = _undo
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private var undoJob: Job? = null
    private var messageJob: Job? = null
    private var initialGuideResolved = false

    init {
        scope.launch {
            repository.observeAllSections().collect { value ->
                _sections.value = value
                _todoIndex.value = _todoIndex.value.coerceIn(0, (value.todos.lastIndex).coerceAtLeast(0))
                _memoIndex.value = _memoIndex.value.coerceIn(0, (value.memos.lastIndex).coerceAtLeast(0))
            }
        }
        scope.launch {
            repository.observeSettings().collect { value ->
                _settings.value = value
                if (!initialGuideResolved) {
                    initialGuideResolved = true
                    if (!value.operationGuideSeen && _overlay.value == null) {
                        _overlay.value = AppOverlay.OperationGuide(firstLaunch = true)
                    }
                }
            }
        }
        scope.launch { repository.observeThemes().collect { _themes.value = it } }
        scope.launch { repository.observeRelations().collect { _relations.value = it } }
    }

    fun selectTab(tab: MainTab) { _tab.value = tab }
    fun selectAdjacentTab(forward: Boolean): Boolean {
        val tabs = MainTab.values()
        val candidate = _tab.value.ordinal + if (forward) 1 else -1
        if (candidate !in tabs.indices) return false
        _tab.value = tabs[candidate]
        return true
    }
    fun openAdd() {
        val kind = when (_tab.value) {
            MainTab.TODOS -> ItemKind.TODO
            MainTab.MEMOS -> ItemKind.MEMO
            MainTab.ALL -> ItemKind.UNSORTED
        }
        _overlay.value = AppOverlay.Add(kind)
    }
    fun openEdit(id: String) { _overlay.value = AppOverlay.Edit(id) }
    fun openSettings() { _overlay.value = AppOverlay.Settings }
    fun openThemes() { _overlay.value = AppOverlay.Themes }
    fun openThemeEditor(theme: ThemeDefinition) { _overlay.value = AppOverlay.ThemeEditor(theme) }
    fun openDataTools() { _overlay.value = AppOverlay.DataTools }
    fun openOperationGuide() { _overlay.value = AppOverlay.OperationGuide(firstLaunch = false) }
    fun saveSettingsAndOpenOperationGuide(value: AppSettings) = scope.launch {
        repository.saveSettings(value)
        _overlay.value = AppOverlay.OperationGuide(firstLaunch = false)
    }
    fun finishOperationGuide(firstLaunch: Boolean) = scope.launch {
        if (!_settings.value.operationGuideSeen) {
            repository.saveSettings(_settings.value.copy(operationGuideSeen = true))
        }
        _overlay.value = if (firstLaunch) null else AppOverlay.Settings
    }
    fun closeOverlay() { _overlay.value = null }

    fun quickAdd(kind: ItemKind, text: String, onAdded: (AppItem) -> Unit = {}) = scope.launch {
        val item = repository.quickAdd(kind, text)
        if (item == null) showMessage("内容を入力してください")
        else {
            onAdded(item)
            showUndo("追加しました")
        }
    }

    fun save(item: AppItem, requiredBeforeIds: Set<String>? = null) = scope.launch {
        runCatching { repository.save(item, requiredBeforeIds) }
            .onSuccess { closeOverlay(); showUndo("保存しました") }
            .onFailure { showMessage(it.message ?: "保存できませんでした") }
    }

    fun addToCalendar(item: AppItem) = scope.launch {
        val detail = item.todo
        val start = detail?.scheduledAtEpochMillis ?: detail?.dueAtEpochMillis ?: detail?.availableFromEpochMillis
        if (start == null) {
            showMessage("行う時刻、期限、または開始可能日時を設定してください")
            return@launch
        }
        val end = start + (detail?.estimatedMinutes ?: 30).coerceIn(1, 1440) * 60_000L
        when (val result = calendarExporter.export(
            CalendarEventDraft(
                itemId = item.id,
                title = item.title,
                notes = item.body,
                startAtEpochMillis = start,
                endAtEpochMillis = end,
            ),
        )) {
            CalendarExportResult.Opened -> showMessage("カレンダーの追加画面を開きました")
            is CalendarExportResult.Added -> showMessage(
                result.calendarName?.let { "$it に追加しました" } ?: "カレンダーに追加しました",
            )
            CalendarExportResult.PermissionDenied -> showMessage("カレンダーへの追加が許可されていません")
            CalendarExportResult.Unavailable -> showMessage("利用できるカレンダーがありません")
            is CalendarExportResult.Failed -> showMessage(result.reason)
        }
    }

    fun delete(id: String) = scope.launch {
        repository.delete(id)
        closeOverlay()
        showUndo("削除しました")
    }

    fun completeCurrent() {
        val item = _sections.value.todos.getOrNull(_todoIndex.value) ?: return
        scope.launch { repository.complete(item.id); showUndo("完了しました") }
    }

    fun deferCurrent() {
        val item = _sections.value.todos.getOrNull(_todoIndex.value) ?: return
        scope.launch {
            val decision = repository.defer(item.id, _settings.value.splitThreshold) ?: return@launch
            showUndo("後で行うことにしました")
            if (_settings.value.splitSuggestionEnabled && decision.shouldSuggestSplit) {
                _overlay.value = AppOverlay.Split(item.id)
            }
        }
    }

    fun convertCurrentMemo() {
        val item = _sections.value.memos.getOrNull(_memoIndex.value) ?: return
        scope.launch { repository.convertMemo(item.id); showUndo("やることにしました") }
    }

    fun archiveCurrentMemo() {
        val item = _sections.value.memos.getOrNull(_memoIndex.value) ?: return
        scope.launch { repository.archiveMemo(item.id); showUndo("メモをしまいました") }
    }

    fun nextTodo() = moveFocus(true, true)
    fun previousTodo() = moveFocus(true, false)
    fun nextMemo() = moveFocus(false, true)
    fun previousMemo() = moveFocus(false, false)

    private fun moveFocus(todo: Boolean, next: Boolean) {
        val count = if (todo) _sections.value.todos.size else _sections.value.memos.size
        val index = if (todo) _todoIndex else _memoIndex
        val candidate = index.value + if (next) 1 else -1
        if (candidate !in 0 until count) {
            showMessage(if (next) "ここが最後です" else "ここが最初です")
        } else index.value = candidate
    }

    fun restore(id: String) = scope.launch { repository.restore(id); showUndo("戻しました") }

    fun moveTodo(id: String, destinationIndex: Int) = scope.launch {
        when (val result = repository.move(id, destinationIndex)) {
            is MoveDecision.Allowed -> showUndo("順番を変更しました")
            is MoveDecision.Rejected -> showMessage(result.message)
        }
    }

    fun moveFree(id: String, destinationIndex: Int) = scope.launch {
        repository.moveFree(id, destinationIndex)
        showUndo("順番を変更しました")
    }

    fun split(id: String, titles: List<String>) = scope.launch {
        when (val result = repository.split(id, titles)) {
            is SplitValidation.Valid -> { closeOverlay(); showUndo("小さく分けました") }
            is SplitValidation.Invalid -> showMessage(result.message)
        }
    }

    fun postponeSplit(id: String) = scope.launch {
        repository.postponeSplitPrompt(id, _settings.value.splitThreshold)
        closeOverlay()
    }

    fun disableSplit(id: String) = scope.launch {
        repository.disableSplitPrompt(id)
        closeOverlay()
        showMessage("このやることでは提案を表示しません")
    }

    fun saveSettings(value: AppSettings) = scope.launch {
        repository.saveSettings(value)
        closeOverlay()
        showMessage("設定を保存しました")
    }

    fun applyTheme(themeId: String) = scope.launch {
        repository.saveSettings(_settings.value.copy(selectedThemeId = themeId))
        showMessage("テーマを変更しました")
    }

    fun saveTheme(theme: ThemeDefinition) = scope.launch {
        when (val result = repository.saveTheme(theme)) {
            is ThemeValidation.Valid -> {
                repository.saveSettings(_settings.value.copy(selectedThemeId = theme.id))
                _overlay.value = AppOverlay.Themes
                showMessage(if (result.warnings.isEmpty()) "テーマを保存しました" else "保存しました。コントラスト警告があります")
            }
            is ThemeValidation.Invalid -> showMessage(result.message)
        }
    }

    fun duplicateTheme(theme: ThemeDefinition) {
        openThemeEditor(theme.copy(id = "custom-${theme.id}-${kotlin.random.Random.nextInt()}", name = "${theme.name} のコピー", builtIn = false))
    }

    fun deleteTheme(id: String) = scope.launch {
        repository.deleteTheme(id)
        if (_settings.value.selectedThemeId == id) repository.saveSettings(_settings.value.copy(selectedThemeId = "standard"))
        showMessage("テーマを削除しました")
    }

    fun exportBackup(onReady: (String) -> Unit) = scope.launch {
        onReady(repository.exportBackupJson())
        showMessage("バックアップを作成しました")
    }

    fun importBackup(value: String) = scope.launch {
        val result = repository.importBackupJson(value)
        showMessage(result.message)
    }

    fun undo() = scope.launch {
        if (repository.undo()) {
            _undo.value = null
            showMessage("元に戻しました")
        } else showMessage("元に戻せる操作はありません")
    }

    fun clearMessage() { _message.value = null }

    private fun showUndo(message: String) {
        _undo.value = UndoNotice(message)
        undoJob?.cancel()
        undoJob = scope.launch {
            delay(_settings.value.undoSeconds * 1_000L)
            _undo.value = null
        }
    }

    private fun showMessage(value: String) {
        _message.value = value
        messageJob?.cancel()
        messageJob = scope.launch { delay(3_000); _message.value = null }
    }
}
