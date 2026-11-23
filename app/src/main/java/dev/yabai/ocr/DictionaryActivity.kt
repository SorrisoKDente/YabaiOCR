package dev.yabai.ocr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import dev.yabai.ocr.core.dict.DictionaryCompiler
import dev.yabai.ocr.dict.DictionaryCatalog
import dev.yabai.ocr.dict.DictionaryDownloader
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.dict.DictionarySource
import dev.yabai.ocr.dict.DictionaryUpdateChecker
import dev.yabai.ocr.dict.DictionaryUpdateScheduler
import dev.yabai.ocr.dict.InstalledDictionary
import dev.yabai.ocr.dict.KanjiDefault
import dev.yabai.ocr.dict.RemoteSource
import dev.yabai.ocr.dict.StoredUpdates
import dev.yabai.ocr.dict.UpdateResult
import dev.yabai.ocr.dict.UpdateState
import dev.yabai.ocr.dict.UpdateStateStore
import dev.yabai.ocr.overlay.actionButton
import dev.yabai.ocr.overlay.actionButtonBackground
import dev.yabai.ocr.overlay.roundedBackground
import dev.yabai.ocr.overlay.textLabel
import dev.yabai.ocr.util.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Download, import and organise dictionaries.
 *
 * Built as plain views rather than a layout file because almost everything on screen is a list whose
 * contents depend on what is installed and what is being downloaded right now. A layout file would
 * be a skeleton with three empty containers and all the real work would still be here.
 */
class DictionaryActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { DictionaryRepository.get(this) }
    private val settings by lazy { AppSettings(this) }

    /**
     * The network side of downloads and checks. A test swaps it for a fake; the screen itself only
     * ever sees the interface (R43).
     */
    internal var remote: RemoteSource = DictionaryDownloader()

    /** A test may inject a checker; the default compares against [remote]. */
    internal var updateChecker: DictionaryUpdateChecker? = null

    private lateinit var installedContainer: LinearLayout
    private lateinit var availableContainer: LinearLayout
    private lateinit var updatesContainer: LinearLayout
    private lateinit var creditsContainer: LinearLayout

    /** The choice of which dictionary answers the kanji card (R30); empty without kanji data. */
    private lateinit var kanjiContainer: LinearLayout

    /** The «old format» banner above the installed list; empty when every dictionary is current (R26). */
    private lateinit var staleContainer: LinearLayout

    /** One download at a time: two 37 MB archives at once is a good way to run out of memory. */
    private var busy: String? = null

    /** The download the «Cancelar» button of the running row can stop (R21). */
    private var downloadJob: Job? = null

    /** A check is running: a second one would only repeat the requests (R43). */
    private var checking = false

    /** The last check, loaded from the store so it survives the process (R43). */
    private var storedUpdates = StoredUpdates(0, emptyMap())

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) toast(getString(R.string.dictionary_updates_no_permission))
        }

    private val pickZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importZip(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings(this).applyTheme(this)
        setContentView(buildContent())
        // WorkManager keeps the schedule across restarts; this only creates it if it went missing
        // (first run after the update, or after the app data was restored).
        DictionaryUpdateScheduler.ensure(this, settings.dictionaryUpdateInterval)
        refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- layout --------------------------------------------------------------------------------

    private fun buildContent(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(36))
        }

        // Toolbar with back button and title
        column.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
                addView(
                    android.widget.ImageButton(this@DictionaryActivity).apply {
                        setImageResource(R.drawable.ic_arrow_back)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(getColor(R.color.text_primary))
                        setOnClickListener { finish() }
                    },
                    LinearLayout.LayoutParams(dp(40), dp(40)),
                )
                addView(
                    textLabel(getString(R.string.dictionary_title), 22f, getColor(R.color.text_primary), bold = true).apply {
                        setPadding(dp(12), 0, 0, 0)
                    },
                )
            },
        )
        column.addView(
            textLabel(getString(R.string.dictionary_subtitle), 13f, getColor(R.color.text_secondary)).apply {
                setPadding(0, dp(2), 0, dp(4))
                setLineSpacing(dp(3).toFloat(), 1f)
            },
        )

        // R35d, moved by R46b: the «Ignore readings in parentheses» switch lives in the main menu's
        // dictionary card now, beside the dictionaries it changes the query for. Nothing of the
        // lookup itself moved with it.
        column.addView(sectionHeader(getString(R.string.dictionary_updates_section)))
        updatesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(updatesContainer)

        column.addView(sectionHeader(getString(R.string.dictionary_installed_section)))
        column.addView(
            textLabel(getString(R.string.dictionary_priority_hint), 11f, getColor(R.color.text_secondary)).apply {
                setPadding(dp(2), 0, dp(2), dp(8))
            },
        )
        installedContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        staleContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(staleContainer)
        column.addView(installedContainer)
        kanjiContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(kanjiContainer)

        column.addView(sectionHeader(getString(R.string.dictionary_import_section)))
        column.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBackground(getColor(R.color.card_bg), dp(12).toFloat(), getColor(R.color.card_stroke))
                setPadding(dp(14), dp(12), dp(14), dp(14))
                addView(
                    textLabel(
                        getString(R.string.dictionary_import_hint),
                        12f,
                        getColor(R.color.text_secondary),
                    ).apply { setLineSpacing(dp(3).toFloat(), 1f) },
                )
                addView(
                    actionButton(getString(R.string.dictionary_import_button)) { pickZip.launch(arrayOf("*/*")) },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(10) },
                )
            },
        )

        column.addView(sectionHeader(getString(R.string.dictionary_available_section)))
        availableContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(availableContainer)

        column.addView(sectionHeader(getString(R.string.dictionary_legal_credits_header)))
        creditsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(getColor(R.color.card_bg), dp(12).toFloat(), getColor(R.color.card_stroke))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            visibility = View.GONE
        }
        val creditsToggle = textLabel(getString(R.string.dictionary_legal_credits_toggle), 12f, getColor(R.color.accent), bold = true).apply {
            setPadding(0, dp(4), 0, dp(8))
            setOnClickListener {
                val open = creditsContainer.isVisible
                creditsContainer.isVisible = !open
                text = getString(if (open) R.string.dictionary_legal_credits_toggle else R.string.dictionary_legal_credits_toggle_open)
            }
        }
        column.addView(creditsToggle)
        column.addView(creditsContainer)

        return ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.panel_bg))
            addView(column)
        }
    }

    private fun sectionHeader(text: String): TextView =
        textLabel(text, 16f, getColor(R.color.accent), bold = true).apply {
            setPadding(dp(2), dp(22), dp(2), dp(2))
        }

    // --- content -------------------------------------------------------------------------------

    private fun refresh() {
        val items = repository.installed()
        storedUpdates = UpdateStateStore(this).load()
        renderStale(items)
        renderUpdates(items)
        renderInstalled(items)
        renderKanjiDefault(items)
        renderAvailable()
        renderCredits(items)
    }

    private fun renderCredits(items: List<InstalledDictionary>) {
        creditsContainer.removeAllViews()
        if (items.isEmpty()) {
            creditsContainer.addView(
                textLabel(getString(R.string.dictionary_installed_empty), 12f, getColor(R.color.text_secondary)),
            )
            return
        }
        val isEnglish = resources.configuration.locales[0].language.equals("en", ignoreCase = true)
        for (item in items) {
            val title = item.info.title.ifBlank { item.displayName }
            val author = item.info.author?.takeIf { it.isNotBlank() } ?: item.sourceName
            val description = item.info.description
            val attribution = item.info.attribution ?: "Licença Yomitan / Domínio público / EDRDG (CC BY-SA 4.0)"
            val revision = item.info.revision
            val termCount = item.info.termCount
            val kanjiCount = item.info.kanjiCount

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(10))
            }
            card.addView(textLabel("• $title", 13f, getColor(R.color.text_primary), bold = true))
            if (author.isNotBlank()) {
                card.addView(textLabel(getString(R.string.credit_author, author), 11f, getColor(R.color.text_secondary)))
            }
            if (description?.isNotBlank() == true) {
                card.addView(textLabel(getString(R.string.credit_description, description), 11f, getColor(R.color.text_secondary)))
            }
            card.addView(textLabel(getString(R.string.credit_attribution, attribution), 11f, getColor(R.color.accent)))
            if (revision.isNotBlank()) {
                card.addView(textLabel(getString(R.string.credit_revision, revision), 11f, getColor(R.color.text_secondary)))
            }
            if (termCount > 0 || kanjiCount > 0) {
                val countStr = buildList {
                    if (termCount > 0) add(if (isEnglish) "$termCount terms" else "$termCount termos")
                    if (kanjiCount > 0) add(if (isEnglish) "$kanjiCount kanji" else "$kanjiCount kanjis")
                }.joinToString(" • ")
                card.addView(textLabel(getString(R.string.credit_entries, countStr), 11f, getColor(R.color.text_secondary)))
            }
            creditsContainer.addView(card)
        }

        creditsContainer.addView(
            textLabel(getString(R.string.general_license_notice), 11f, getColor(R.color.text_secondary)).apply {
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            }
        )
    }

    /**
     * The update card: the last check, the two buttons and the automatic-check choice (R43).
     *
     * It sits above the installed list because that is what it talks about: the row of each
     * dictionary then carries its own «Atualizar», so the card does not have to enumerate them.
     */
    private fun renderUpdates(items: List<InstalledDictionary>) {
        updatesContainer.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(getColor(R.color.card_bg), dp(12).toFloat(), getColor(R.color.card_stroke))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val status = textLabel(updateStatusText(items), 12f, getColor(R.color.text_secondary)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        card.addView(status)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }

        val check = actionButton(
            getString(
                if (checking) R.string.dictionary_updates_checking
                else R.string.dictionary_updates_check,
            ),
        ) { }
        check.isEnabled = !checking && busy == null
        check.setOnClickListener { startCheck(status, check) }
        buttons.addView(check)

        val updated = availableEntries()
        if (updated.isNotEmpty()) {
            val all = actionButton(getString(R.string.dictionary_update_all)) { }
            all.isEnabled = busy == null && !checking
            all.setOnClickListener { startUpdateAll(updated, status, all) }
            buttons.addView(
                all,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(8) },
            )
        }
        card.addView(buttons)

        val automatic = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        automatic.addView(
            textLabel(getString(R.string.dictionary_updates_auto), 12f, getColor(R.color.text_secondary)),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        automatic.addView(
            actionButton(intervalLabel(settings.dictionaryUpdateInterval)) { chooseInterval() },
        )
        card.addView(automatic)

        card.addView(
            textLabel(getString(R.string.dictionary_updates_hint), 11f, getColor(R.color.text_secondary)).apply {
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            },
        )

        updatesContainer.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun updateStatusText(items: List<InstalledDictionary>): String {
        if (storedUpdates.checkedAt == 0L) {
            return getString(R.string.dictionary_updates_never)
        }
        val known = items.map { it.id }.toSet()
        val results = storedUpdates.results.values.filter { it.id in known }
        if (results.isEmpty()) return getString(R.string.dictionary_updates_nothing)
        val available = results.count { it.hasUpdate }
        val failed = results.count { it.state == UpdateState.FAILED }
        val summary = when {
            available > 0 -> resources.getQuantityString(
                R.plurals.dictionary_updates_available_count,
                available,
                available,
            )
            failed > 0 -> getString(R.string.dictionary_updates_failed_count, failed)
            else -> getString(R.string.dictionary_updates_all_current)
        }
        val whenText = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(storedUpdates.checkedAt))
        return getString(R.string.dictionary_updates_last_check, whenText, summary)
    }

    private fun availableEntries(): List<DictionaryCatalog.Entry> {
        val installed = repository.installed().map { it.id }.toSet()
        return storedUpdates.results.values
            .filter { it.hasUpdate && it.id in installed }
            .mapNotNull { DictionaryCatalog.find(it.id) }
    }

    /**
     * The banner that says the definitions come from an older format (R26).
     *
     * The per-dictionary line is the last line of a card, under the counts and the source, and it
     * only helps someone who already knows to look for it. The banner sits above the list and names
     * the dictionaries, so the screen says it on its own the first time it is opened after an
     * update — which is exactly when the definitions look unchanged.
     */
    private fun renderStale(items: List<InstalledDictionary>) {
        staleContainer.removeAllViews()
        val stale = items.filter { it.info.outdated }
        if (stale.isEmpty()) return
        staleContainer.addView(
            textLabel(
                getString(R.string.dictionary_stale_banner, stale.joinToString("; ") { it.displayName }),
                12f,
                getColor(R.color.text_primary),
            ).apply {
                background = roundedBackground(0x33FFB300, dp(12).toFloat(), 0x66FFB300)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setLineSpacing(dp(3).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(10) }
            },
        )
    }

    private fun renderInstalled(items: List<InstalledDictionary>) {
        installedContainer.removeAllViews()

        if (items.isEmpty()) {
            installedContainer.addView(
                textLabel(getString(R.string.dictionary_installed_empty), 13f, getColor(R.color.text_secondary))
                    .apply { setPadding(dp(2), dp(4), dp(2), dp(4)) },
            )
            return
        }

        for ((index, item) in items.withIndex()) {
            installedContainer.addView(installedRow(item, index, items.size))
        }
    }

    private fun installedRow(item: InstalledDictionary, index: Int, total: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(getColor(R.color.card_bg), dp(12).toFloat(), getColor(R.color.card_stroke))
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }

        card.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    textLabel(item.info.title, 16f, getColor(R.color.text_primary), bold = true),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    CheckBox(this@DictionaryActivity).apply {
                        text = getString(R.string.dictionary_enable)
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 12f
                        isChecked = item.enabled
                        setOnCheckedChangeListener { _, checked ->
                            repository.setEnabled(item.id, checked)
                            refresh()
                        }
                    },
                )
            },
        )

        card.addView(
            textLabel(describe(item), 12f, getColor(R.color.text_secondary)).apply {
                setPadding(0, dp(2), 0, 0)
                setLineSpacing(dp(2).toFloat(), 1f)
            },
        )

        val result = storedUpdates.results[item.id]
        updateLine(result)?.let { (text, color) ->
            card.addView(
                textLabel(text, 12f, color).apply {
                    setPadding(0, dp(4), 0, 0)
                    setLineSpacing(dp(2).toFloat(), 1f)
                },
            )
        }

        // The update's own progress lives here, next to the dictionary it belongs to: the same
        // download UI as the «Disponíveis» rows, without leaving the row that found the update.
        val status = textLabel("", 12f, getColor(R.color.accent)).apply {
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(status)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }
        val entry = DictionaryCatalog.find(item.sourceId.ifBlank { item.id })
        val updateOffered = entry != null && result?.canRefresh == true
        // R47d: a dictionary the check has nothing to offer is not stuck with the old format. The
        // catalog still has the archive; downloading it again and compiling with this app is the
        // reinstall the «compiled by an older version» banner talks about.
        val reinstallOffered = entry != null && !updateOffered && item.info.outdated
        val downloadOffered = updateOffered || reinstallOffered
        if (updateOffered) {
            val update = actionButton(getString(R.string.dictionary_update)) { }
            update.setOnClickListener {
                startDownload(entry!!, status, update)
            }
            actions.addView(update)
        } else if (reinstallOffered) {
            val reinstall = actionButton(getString(R.string.dictionary_reinstall)) { }
            reinstall.setOnClickListener {
                startDownload(entry!!, status, reinstall)
            }
            actions.addView(reinstall)
        }
        actions.addView(
            actionButton("↑", onClick = {
                repository.move(item.id, -1)
                refresh()
            }).apply {
                if (index == 0) isEnabled = false
                contentDescription = getString(R.string.dictionary_up)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { if (downloadOffered) marginStart = dp(8) }
            },
        )
        actions.addView(
            actionButton("↓", onClick = {
                repository.move(item.id, 1)
                refresh()
            }).apply {
                if (index == total - 1) isEnabled = false
                contentDescription = getString(R.string.dictionary_down)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(6) }
            },
        )
        actions.addView(
            actionButton(
                getString(R.string.dictionary_delete),
                background = getColor(R.color.warn),
                onClick = { confirmDelete(item) },
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(10) }
            },
        )
        card.addView(actions)

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
        }
    }

    /**
     * The choice of which dictionary answers the kanji card (R30).
     *
     * It only appears when there is a dictionary with kanji data: with none installed the card has
     * its own explanation in «Ler kanji», and a chooser over an empty list would be one more thing
     * that does not work. The stored id is cleared here when the dictionary it names was disabled or
     * removed, so the next time it reappears the screen says «todos» honestly.
     */
    private fun renderKanjiDefault(items: List<InstalledDictionary>) {
        kanjiContainer.removeAllViews()
        val candidates = KanjiDefault.candidates(items)
        if (candidates.isEmpty()) return

        val stored = settings.kanjiDictionary
        val chosen = candidates.firstOrNull { it.id == stored }
        if (stored.isNotBlank() && chosen == null) settings.kanjiDictionary = ""

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(getColor(R.color.card_bg), dp(12).toFloat(), getColor(R.color.card_stroke))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        card.addView(textLabel(getString(R.string.dictionary_kanji_title), 16f, getColor(R.color.text_primary), bold = true))
        card.addView(
            textLabel(getString(R.string.dictionary_kanji_hint), 12f, getColor(R.color.text_secondary)).apply {
                setPadding(0, dp(4), 0, 0)
                setLineSpacing(dp(3).toFloat(), 1f)
            },
        )
        card.addView(
            actionButton(chosen?.displayName ?: getString(R.string.dictionary_kanji_all)) {
                chooseKanjiDefault(candidates)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) }
            },
        )

        kanjiContainer.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )
    }

    private fun chooseKanjiDefault(candidates: List<InstalledDictionary>) {
        val labels = ArrayList<String>(candidates.size + 1)
        labels.add(getString(R.string.dictionary_kanji_all))
        labels.addAll(candidates.map { it.displayName })
        AlertDialog.Builder(this)
            .setTitle(R.string.dictionary_kanji_title)
            .setItems(labels.toTypedArray()) { _, which ->
                settings.kanjiDictionary = if (which == 0) "" else candidates[which - 1].id
                refresh()
            }
            .show()
    }

    private fun describe(item: InstalledDictionary): String {
        val parts = ArrayList<String>(5)
        if (item.info.termCount > 0) {
            parts.add(getString(R.string.dictionary_terms, formatCount(item.info.termCount)))
        }
        if (item.info.kanjiCount > 0) {
            parts.add(getString(R.string.dictionary_kanji_count, formatCount(item.info.kanjiCount)))
        }
        parts.add(formatBytes(item.sizeBytes))
        if (item.info.revision.isNotBlank()) {
            parts.add(getString(R.string.dictionary_release, item.info.revision))
        }
        val lines = ArrayList<String>(2)
        lines.add(parts.joinToString(" · "))
        if (item.info.outdated) lines.add(getString(R.string.dictionary_outdated))
        return lines.joinToString("\n")
    }

    /** The one line an installed row adds after a check, or null when there is nothing to say. */
    private fun updateLine(result: UpdateResult?): Pair<String, Int>? {
        if (result == null) return null
        return when (result.state) {
            UpdateState.AVAILABLE -> Pair(
                if (result.remoteLabel.isBlank()) {
                    getString(R.string.dictionary_update_available)
                } else {
                    getString(R.string.dictionary_update_available_version, result.remoteLabel)
                },
                getColor(R.color.accent),
            )
            UpdateState.UNKNOWN -> Pair(
                getString(R.string.dictionary_update_unknown),
                getColor(R.color.text_secondary),
            )
            UpdateState.FAILED -> Pair(
                getString(R.string.dictionary_update_failed, toLocalizedUpdateMessage(result.message)),
                getColor(R.color.text_secondary),
            )
            UpdateState.MANUAL -> if (storedUpdates.checkedAt > 0) {
                Pair(getString(R.string.dictionary_update_manual), getColor(R.color.text_secondary))
            } else {
                null
            }
            UpdateState.CURRENT -> null
        }
    }

    private fun intervalLabel(interval: UpdateInterval): String = when (interval) {
        UpdateInterval.OFF -> getString(R.string.dictionary_updates_off)
        UpdateInterval.DAILY -> getString(R.string.dictionary_updates_daily)
        UpdateInterval.WEEKLY -> getString(R.string.dictionary_updates_weekly)
        UpdateInterval.MONTHLY -> getString(R.string.dictionary_updates_monthly)
    }

    private fun toLocalizedUpdateMessage(message: String): String = when (message) {
        "o catálogo não informou a revisão" -> getString(R.string.dictionary_update_err_no_revision)
        "o catálogo não informou de onde baixar" -> getString(R.string.dictionary_update_err_no_url)
        "esta cópia foi instalada antes das verificações; atualize uma vez para ativá-las" -> getString(R.string.dictionary_update_err_legacy)
        "o servidor não informou uma versão comparável" -> getString(R.string.dictionary_update_err_no_comparable_version)
        else -> message
    }

    private fun chooseInterval() {
        val values = UpdateInterval.entries.toTypedArray()
        val labels = values.map { intervalLabel(it) }.toTypedArray()
        var checked = values.indexOf(settings.dictionaryUpdateInterval)
        if (checked < 0) checked = 0
        AlertDialog.Builder(this)
            .setTitle(R.string.dictionary_updates_auto_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val interval = values[which]
                settings.dictionaryUpdateInterval = interval
                DictionaryUpdateScheduler.set(this, interval)
                if (interval != UpdateInterval.OFF) requestNotificationPermission()
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.dictionary_cancel, null)
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun renderAvailable() {
        availableContainer.removeAllViews()
        for (entry in DictionaryCatalog.entries) {
            availableContainer.addView(availableRow(entry))
        }
    }

    private fun availableRow(entry: DictionaryCatalog.Entry): View {
        val installed = repository.installed().any { it.id == entry.id }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(getColor(R.color.card_bg), dp(12).toFloat(), getColor(R.color.card_stroke))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        card.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    textLabel(entry.name, 16f, getColor(R.color.text_primary), bold = true),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(textLabel(entry.sizeLabel, 12f, getColor(R.color.text_secondary)))
            },
        )

        card.addView(
            textLabel(entry.description, 12f, getColor(R.color.text_secondary)).apply {
                setPadding(0, dp(4), 0, 0)
                setLineSpacing(dp(3).toFloat(), 1f)
            },
        )

        val status = textLabel("", 12f, getColor(R.color.accent)).apply { setPadding(0, dp(6), 0, 0) }
        status.visibility = View.GONE
        card.addView(status)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }

        // R47b: the row in «Disponíveis» turns into the same «Remover» as the installed card, so it
        // has to be the same red — the accent blue read as another «Baixar».
        val action = actionButton(
            if (installed) getString(R.string.dictionary_delete) else getString(R.string.dictionary_download),
            background = if (installed) getColor(R.color.warn) else actionButtonBackground,
        ) { }
        // Set after the button exists: the running download needs the button itself, to turn it into
        // «Cancelar» and then back into «Baixar» (R21).
        action.setOnClickListener {
            if (installed) {
                repository.installed().firstOrNull { it.id == entry.id }?.let { confirmDelete(it) }
            } else {
                startDownload(entry, status, action)
            }
        }
        buttons.addView(action)
        card.addView(buttons)
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
        }
    }

    // --- work ----------------------------------------------------------------------------------

    private fun confirmDelete(item: InstalledDictionary) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.dictionary_delete_confirm, item.info.title))
            .setNegativeButton(R.string.dictionary_cancel, null)
            .setPositiveButton(R.string.dictionary_delete) { _, _ ->
                repository.delete(item.id)
                // A conclusion about a dictionary that no longer exists must not keep offering to
                // reinstall it under «Atualizar tudo» (R43).
                UpdateStateStore(this).forget(item.id)
                refresh()
            }
            .show()
    }

    /**
     * Checks every installed dictionary that has a publisher, and records the answer (R43).
     *
     * Nothing is downloaded here: a check is one 1 KB descriptor or one HEAD per dictionary, so the
     * button can be pressed without thinking about data. The download and the compile happen when
     * the user taps «Atualizar», in the row the check just marked.
     */
    private fun startCheck(status: TextView, button: TextView) {
        if (checking || busy != null) return
        val items = repository.installed()
        if (items.none { DictionaryCatalog.find(it.sourceId.ifBlank { it.id }) != null }) {
            toast(getString(R.string.dictionary_updates_nothing))
            return
        }

        checking = true
        button.isEnabled = false
        button.text = getString(R.string.dictionary_updates_checking)
        status.text = getString(R.string.dictionary_updates_checking)
        val checker = updateChecker ?: DictionaryUpdateChecker(remote)
        scope.launch {
            try {
                val results = checker.checkAll(items)
                UpdateStateStore(this@DictionaryActivity).save(results, System.currentTimeMillis())
                checking = false
                refresh()
            } catch (t: CancellationException) {
                checking = false
            } catch (t: Throwable) {
                checking = false
                button.isEnabled = true
                button.text = getString(R.string.dictionary_updates_check)
                status.text = getString(
                    R.string.dictionary_update_failed,
                    t.message ?: t.javaClass.simpleName,
                )
            }
        }
    }

    /** Downloads and compiles the new version of a dictionary, replacing it in place. */
    private suspend fun fetchAndInstall(
        entry: DictionaryCatalog.Entry,
        onPhase: (String) -> Unit,
    ): InstalledDictionary {
        val archive = File(cacheDir, "dict-${entry.id}.zip")
        try {
            val downloaded = remote.download(entry, archive) { read, total ->
                // This callback runs on Dispatchers.IO (inside the downloader's withContext). The
                // status it writes is a TextView, so it has to hop to the main thread first (R43).
                onUi {
                    onPhase(
                        if (total > 0) {
                            getString(
                                R.string.dictionary_downloading,
                                "${(read * 100 / total)}%  (${formatBytes(read)})",
                            )
                        } else {
                            getString(R.string.dictionary_downloading, formatBytes(read))
                        },
                    )
                }            }
            onUi { onPhase(getString(R.string.dictionary_installing, "…")) }
            val source = DictionarySource(
                catalogId = entry.id,
                // Informational only; the check uses the catalog, not this stored URL.
                url = entry.url ?: entry.indexUrl.orEmpty(),
                signature = downloaded.signature,
            )
            return repository.install(archive, entry.id, entry.name, source) { phase ->
                // The compiler reports from its own worker thread, for the same reason.
                onUi { onPhase(getString(R.string.dictionary_installing, phase)) }
            }
        } finally {
            archive.delete()
        }
    }

    private fun startDownload(
        entry: DictionaryCatalog.Entry,
        status: TextView,
        button: TextView,
    ) {
        if (checking) {
            // Installing while a check is in flight would let the check record a conclusion about
            // the copy that was just replaced (R43).
            toast(getString(R.string.dictionary_updates_checking))
            return
        }
        if (busy != null) {
            toast(getString(R.string.dictionary_download_failed, getString(R.string.dictionary_error_busy)))
            return
        }
        busy = entry.id
        status.visibility = View.VISIBLE
        status.text = getString(R.string.dictionary_downloading, "0%")
        // The row's own button is the cancel button while its download runs (R21): a 37 MB archive
        // on mobile data is exactly the thing one wants to take back.
        button.text = getString(R.string.dictionary_cancel)
        button.setOnClickListener { downloadJob?.cancel() }

        downloadJob = scope.launch {
            try {
                val installed = fetchAndInstall(entry) { detail -> status.text = detail }
                // The update was installed: whatever the last check said about this id is no longer
                // true, and a stale badge under a fresh dictionary would be worse than no badge.
                UpdateStateStore(this@DictionaryActivity).forget(entry.id)
                toast(getString(R.string.dictionary_installed_done, installed.info.title))
            } catch (t: CancellationException) {
                toast(getString(R.string.dictionary_download_cancelled))
            } catch (t: Throwable) {
                showFailure(R.string.dictionary_download_failed, t)
            } finally {
                busy = null
                downloadJob = null
                // Rebuild the screen only after `busy` is clear. renderUpdates() decides whether the
                // new buttons are enabled from `busy`, so refreshing inside the try left «Verificar»
                // and «Atualizar tudo» dead until the screen was reopened (R43, device report).
                if (!isFinishing && !isDestroyed) refresh()
            }
        }
    }

    /** Updates every dictionary the last check marked, one after another, with one Cancel. */
    private fun startUpdateAll(
        entries: List<DictionaryCatalog.Entry>,
        status: TextView,
        button: TextView,
    ) {
        if (busy != null || checking) {
            toast(getString(R.string.dictionary_download_failed, getString(R.string.dictionary_error_busy)))
            return
        }
        busy = "update-all"
        status.visibility = View.VISIBLE
        button.text = getString(R.string.dictionary_cancel)
        button.setOnClickListener { downloadJob?.cancel() }

        downloadJob = scope.launch {
            var done = 0
            var failed = 0
            try {
                for ((index, entry) in entries.withIndex()) {
                    status.text = getString(
                        R.string.dictionary_updating_named,
                        entry.name,
                        index + 1,
                        entries.size,
                    )
                    try {
                        fetchAndInstall(entry) { detail ->
                            status.text = getString(
                                R.string.dictionary_updating_progress,
                                entry.name,
                                index + 1,
                                entries.size,
                                detail,
                            )
                        }
                        UpdateStateStore(this@DictionaryActivity).forget(entry.id)
                        done++
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        failed++
                        showFailure(R.string.dictionary_download_failed, t)
                    }
                }
                if (failed == 0) {
                    toast(resources.getQuantityString(R.plurals.dictionary_update_all_done, done, done))
                } else {
                    toast(getString(R.string.dictionary_update_all_partial, done, failed))
                }
            } catch (t: CancellationException) {
                toast(getString(R.string.dictionary_download_cancelled))
            } finally {
                busy = null
                downloadJob = null
                // After `busy` is clear, so the rebuilt card does not come out disabled (R43).
                if (!isFinishing && !isDestroyed) refresh()
            }
        }
    }

    private fun importZip(uri: Uri) {
        if (busy != null) {
            toast(getString(R.string.dictionary_import_failed, getString(R.string.dictionary_error_busy)))
            return
        }
        busy = "import"
        val name = displayName(uri) ?: "importado.zip"

        scope.launch {
            val archive = File(cacheDir, "import-dict.zip")
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { getString(R.string.dictionary_error_open_file) }
                        archive.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                toast(getString(R.string.dictionary_installing, ""))
                val installed = repository.install(
                    zip = archive,
                    id = DictionaryRepository.importedId(name),
                    displayName = name,
                )
                toast(getString(R.string.dictionary_installed_done, installed.info.title))
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                showFailure(R.string.dictionary_import_failed, t)
            } finally {
                archive.delete()
                busy = null
                // Same order as the downloads: a refresh while «busy» is set rebuilds the update
                // card with its buttons disabled (R43).
                if (!isFinishing && !isDestroyed) refresh()
            }
        }
    }

    private fun showFailure(template: Int, error: Throwable) {
        val detail = when {
            error is DictionaryCompiler.NotADictionaryException -> error.message.orEmpty()
            error is java.util.zip.ZipException -> "o arquivo não é um .zip válido"
            else -> error.message ?: error.javaClass.simpleName
        }
        toast(getString(template, detail))
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment
    }

    // --- formatting ----------------------------------------------------------------------------

    private fun formatCount(value: Int): String = String.format(Locale.getDefault(), "%,d", value)

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    /**
     * Runs [block] on the main thread, unless this screen is already gone.
     *
     * Download and compile progress is reported from `Dispatchers.IO` — the downloader calls back
     * inside its `withContext(IO)`, the compiler from its own worker. On a device, touching the
     * status `TextView` from there throws `CalledFromWrongThreadException`, which aborted the work at
     * the first progress report: no percentage showed, no dictionary was replaced, and every entry
     * in «Atualizar tudo» counted as a failure (R43, device report). Robolectric does not enforce
     * the thread affinity, so the tests passed while the phone could not update anything.
     */
    internal fun onUi(block: () -> Unit) {
        if (isFinishing || isDestroyed) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            runOnUiThread { if (!isFinishing && !isDestroyed) block() }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, DictionaryActivity::class.java)
    }
}
