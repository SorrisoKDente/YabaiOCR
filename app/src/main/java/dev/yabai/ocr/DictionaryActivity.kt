package dev.yabai.ocr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import dev.yabai.ocr.core.dict.DictionaryCompiler
import dev.yabai.ocr.dict.DictionaryCatalog
import dev.yabai.ocr.dict.DictionaryDownloader
import dev.yabai.ocr.dict.DictionaryRepository
import dev.yabai.ocr.dict.InstalledDictionary
import dev.yabai.ocr.overlay.actionButton
import dev.yabai.ocr.overlay.roundedBackground
import dev.yabai.ocr.overlay.textLabel
import dev.yabai.ocr.util.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    private val downloader = DictionaryDownloader()

    private lateinit var installedContainer: LinearLayout
    private lateinit var availableContainer: LinearLayout

    /** One download at a time: two 37 MB archives at once is a good way to run out of memory. */
    private var busy: String? = null

    private val pickZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importZip(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
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

        column.addView(
            textLabel(getString(R.string.dictionary_title), 26f, getColor(R.color.text_primary), bold = true),
        )
        column.addView(
            textLabel(getString(R.string.dictionary_subtitle), 13f, getColor(R.color.text_secondary)).apply {
                setPadding(0, dp(6), 0, dp(4))
                setLineSpacing(dp(3).toFloat(), 1f)
            },
        )

        column.addView(sectionHeader(getString(R.string.dictionary_installed_section)))
        column.addView(
            textLabel(getString(R.string.dictionary_priority_hint), 11f, getColor(R.color.text_secondary)).apply {
                setPadding(dp(2), 0, dp(2), dp(8))
            },
        )
        installedContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(installedContainer)

        column.addView(sectionHeader(getString(R.string.dictionary_import_section)))
        column.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBackground(0x14FFFFFF, dp(12).toFloat(), 0x22FFFFFF)
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
        renderInstalled(repository.installed())
        renderAvailable()
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
            background = roundedBackground(0x14FFFFFF, dp(12).toFloat(), 0x22FFFFFF)
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

        card.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(6), 0, 0)
                addView(actionButton("↑", onClick = {
                    repository.move(item.id, -1)
                    refresh()
                }).apply {
                    if (index == 0) isEnabled = false
                    contentDescription = getString(R.string.dictionary_up)
                })
                addView(
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
                addView(
                    actionButton(getString(R.string.dictionary_delete), onClick = { confirmDelete(item) }).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { marginStart = dp(10) }
                    },
                )
            },
        )

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) }
        }
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
        val attribution = item.info.attribution?.takeIf { it.isNotBlank() }
        val text = parts.joinToString(" · ")
        return if (attribution != null) "$text\n${getString(R.string.dictionary_attribution, attribution)}" else text
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
            background = roundedBackground(0x14FFFFFF, dp(12).toFloat(), 0x22FFFFFF)
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

        val action = actionButton(
            if (installed) getString(R.string.dictionary_delete) else getString(R.string.dictionary_download),
        ) {
            if (installed) {
                repository.installed().firstOrNull { it.id == entry.id }?.let { confirmDelete(it) }
            } else {
                startDownload(entry, status)
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
                refresh()
            }
            .show()
    }

    private fun startDownload(entry: DictionaryCatalog.Entry, status: TextView) {
        if (busy != null) {
            toast(getString(R.string.dictionary_download_failed, "outro download em andamento"))
            return
        }
        busy = entry.id
        status.visibility = View.VISIBLE
        status.text = getString(R.string.dictionary_downloading, "0%")

        scope.launch {
            val archive = File(cacheDir, "dict-${entry.id}.zip")
            try {
                downloader.download(entry, archive) { read, total ->
                    if (total > 0) {
                        status.text = getString(
                            R.string.dictionary_downloading,
                            "${(read * 100 / total)}%  (${formatBytes(read)})",
                        )
                    } else {
                        status.text = getString(R.string.dictionary_downloading, formatBytes(read))
                    }
                }

                status.text = getString(R.string.dictionary_installing, "…")
                val installed = repository.install(archive, entry.id, entry.name) { phase ->
                    status.text = getString(R.string.dictionary_installing, phase)
                }
                toast(getString(R.string.dictionary_installed_done, installed.info.title))
                refresh()
            } catch (t: Throwable) {
                status.visibility = View.GONE
                showFailure(R.string.dictionary_download_failed, t)
            } finally {
                archive.delete()
                busy = null
            }
        }
    }

    private fun importZip(uri: Uri) {
        if (busy != null) {
            toast(getString(R.string.dictionary_import_failed, "outro download em andamento"))
            return
        }
        busy = "import"
        val name = displayName(uri) ?: "importado.zip"

        scope.launch {
            val archive = File(cacheDir, "import-dict.zip")
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "não foi possível abrir o arquivo" }
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
                refresh()
            } catch (t: Throwable) {
                showFailure(R.string.dictionary_import_failed, t)
            } finally {
                archive.delete()
                busy = null
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

    companion object {
        fun intent(context: Context): Intent = Intent(context, DictionaryActivity::class.java)
    }
}
