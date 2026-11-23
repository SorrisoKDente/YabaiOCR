package dev.yabai.ocr

import android.content.Context
import android.os.Looper
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.yabai.ocr.core.dict.DictionaryCompiler
import dev.yabai.ocr.dict.DictionaryUpdateChecker
import dev.yabai.ocr.dict.DictionaryUpdateScheduler
import dev.yabai.ocr.dict.DownloadedDictionary
import dev.yabai.ocr.dict.InstalledDictionary
import dev.yabai.ocr.dict.RemoteSignature
import dev.yabai.ocr.dict.RemoteSource
import dev.yabai.ocr.dict.UpdateResult
import dev.yabai.ocr.dict.UpdateState
import dev.yabai.ocr.dict.UpdateStateStore
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The «Atualizações» card and the per-row «Atualizar» (R43).
 *
 * The screen has two jobs: after a check, say which dictionaries have something newer and give each
 * of them a button that does the download; and let the user choose whether the check happens on its
 * own. A row that cannot be checked (a dictionary imported from a file) must say so instead of
 * offering a button that can only fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "pt-rBR")
class DictionaryUpdatesScreenTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun install(
        id: String,
        title: String,
        source: String,
        revision: String = "2026-01-01",
        sourceId: String = "",
        compiledWith: Int = DictionaryCompiler.FORMAT_VERSION,
    ) {
        val directory = File(context.filesDir, "dictionaries/$id")
        directory.mkdirs()
        File(directory, "meta.json").writeText(
            """
            {
              "title": "$title",
              "revision": "$revision",
              "format": 3,
              "termCount": 10,
              "kanjiCount": 0,
              "compiledWith": $compiledWith,
              "sourceName": "$source",
              "sourceId": "$sourceId"
            }
            """.trimIndent(),
        )
    }

    private fun store(result: UpdateResult, checkedAt: Long = 1_700_000_000_000) {
        UpdateStateStore(context).save(listOf(result), checkedAt)
    }

    /** A minimal archive for the downloader to hand back. */
    private fun archive(revision: String, definition: String): File {
        val file = File(context.cacheDir, "update-$revision.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(entry: String, text: String) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(text.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            put("index.json", """{"title":"JMdict","revision":"$revision","format":3}""")
            put("term_bank_1.json", """[["食べる","たべる","v1","",100,[["$definition"]],1,""]]""")
        }
        return file
    }

    private class FakeRemote(
        private val zip: File,
        private val signature: RemoteSignature,
    ) : RemoteSource {
        override suspend fun download(
            entry: dev.yabai.ocr.dict.DictionaryCatalog.Entry,
            target: File,
            onProgress: (Long, Long) -> Unit,
        ): DownloadedDictionary {
            onProgress(zip.length(), zip.length())
            zip.copyTo(target, overwrite = true)
            return DownloadedDictionary(target, signature)
        }

        override suspend fun head(url: String): RemoteSignature = signature

        override suspend fun readText(url: String): String = ""
    }

    /** Reports progress and then waits, so the test can look at the screen mid-download. */
    private class PausingRemote(
        private val zip: File,
        private val signature: RemoteSignature,
    ) : RemoteSource {
        val release = CompletableDeferred<Unit>()

        override suspend fun download(
            entry: dev.yabai.ocr.dict.DictionaryCatalog.Entry,
            target: File,
            onProgress: (Long, Long) -> Unit,
        ): DownloadedDictionary {
            onProgress(zip.length(), zip.length())
            release.await()
            zip.copyTo(target, overwrite = true)
            return DownloadedDictionary(target, signature)
        }

        override suspend fun head(url: String): RemoteSignature = signature

        override suspend fun readText(url: String): String = ""
    }

    /** Runs the main looper until [condition] holds, so the test does not depend on timing. */
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !condition()) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        ShadowLooper.idleMainLooper()
        assertTrue("the screen never reached the expected state", condition())
    }

    private fun activity() = Robolectric.buildActivity(DictionaryActivity::class.java).setup().get()

    private fun labels(activity: DictionaryActivity): List<TextView> =
        labels(activity.window.decorView as ViewGroup)

    private fun labels(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is TextView -> found.add(child)
                    is ViewGroup -> walk(child)
                }
            }
        }
        walk(root)
        return found
    }

    private fun button(activity: DictionaryActivity, text: String): TextView? =
        labels(activity).firstOrNull { it.text.toString() == text }

    @Test
    fun `a stored check marks the row and offers the update`() {
        install("jmdict-en", "JMdict", "JMdict (inglês)", sourceId = "jmdict-en")
        store(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "2026.10.30"))

        val texts = labels(activity()).map { it.text.toString() }

        assertTrue(
            "the row does not say an update exists: $texts",
            texts.any { it.contains("Atualização disponível") && it.contains("2026.10.30") },
        )
        assertTrue("the row should offer «Atualizar»: $texts", texts.contains("Atualizar"))
        assertTrue(
            "the card should summarise the last check: $texts",
            texts.any { it.startsWith("Última verificação") && it.contains("1 atualização disponível") },
        )
        assertTrue("the check should be offered: $texts", texts.contains("Verificar atualizações"))
    }

    @Test
    fun `checking now stores the answer and shows it without leaving the screen`() {
        install("jmdict-en", "JMdict", "JMdict (inglês)")
        val activity = activity()
        activity.updateChecker = object : DictionaryUpdateChecker() {
            override suspend fun checkAll(installed: List<InstalledDictionary>): List<UpdateResult> =
                listOf(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "2026.10.30"))
        }

        button(activity, "Verificar atualizações")!!.performClick()
        ShadowLooper.idleMainLooper()

        val texts = labels(activity).map { it.text.toString() }
        assertTrue("the check result should appear on the row: $texts", texts.contains("Atualizar"))
        assertEquals(
            UpdateState.AVAILABLE,
            UpdateStateStore(context).load().results["jmdict-en"]?.state,
        )
    }

    @Test
    fun `the update button downloads, replaces the dictionary and clears the badge`() {
        install("jmdict-en", "JMdict", "JMdict (inglês)", revision = "2026-01-01", sourceId = "jmdict-en")
        store(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "2026-10-30"))
        val activity = activity()
        activity.remote = FakeRemote(
            archive("2026-10-30", "comer (novo)"),
            RemoteSignature(etag = "\"new\"", length = 123),
        )

        button(activity, "Atualizar")!!.performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline &&
            UpdateStateStore(context).load().results.containsKey("jmdict-en")
        ) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()

        val repository = dev.yabai.ocr.dict.DictionaryRepository.get(context)
        val item = repository.installed().single()
        assertEquals("the new archive has to be what the dictionary now answers with", "2026-10-30", item.info.revision)
        assertEquals("jmdict-en", item.sourceId)
        assertTrue("the update has to stay enabled", item.enabled)
        assertNull(
            "a resolved update must not leave its old badge behind",
            UpdateStateStore(context).load().results["jmdict-en"],
        )
        assertFalse(
            "there is nothing left to update: ${labels(activity).map { it.text }}",
            labels(activity).any { it.text.toString() == "Atualizar" },
        )
        assertTrue(
            "the new definition must be live: ${repository.lookupEngine()!!.lookup("食べる").first().entry.definition}",
            repository.lookupEngine()!!.lookup("食べる").first().entry.definition.contains("comer (novo)"),
        )
    }

    /**
     * R47d: a dictionary compiled by an older app version offered no way out when the publisher had
     * nothing newer to announce. «Reinstalar» downloads the catalog's current archive and compiles
     * it with this app, which is exactly what the «old format» banner asks for.
     */
    @Test
    fun `an old-format dictionary can be reinstalled even with nothing to update`() {
        install(
            "jmdict-en",
            "JMdict",
            "JMdict (inglês)",
            revision = "2026-01-01",
            sourceId = "jmdict-en",
            compiledWith = DictionaryCompiler.FORMAT_VERSION - 1,
        )
        val activity = activity()
        activity.remote = FakeRemote(
            archive("2026-10-30", "comer (reinstalado)"),
            RemoteSignature(etag = "\"new\"", length = 123),
        )

        val reinstall = button(activity, "Reinstalar")
        assertNotNull(
            "an old-format dictionary has no way to be reinstalled: ${labels(activity).map { it.text }}",
            reinstall,
        )
        reinstall!!.performClick()
        awaitMain {
            dev.yabai.ocr.dict.DictionaryRepository.get(context).installed()
                .singleOrNull()?.info?.outdated == false
        }

        val repository = dev.yabai.ocr.dict.DictionaryRepository.get(context)
        assertEquals("the reinstall has to be what the dictionary now answers with", "2026-10-30", repository.installed().single().info.revision)
        assertFalse(
            "the fresh copy is still treated as old: ${labels(activity).map { it.text }}",
            labels(activity).any { it.text.toString() == "Reinstalar" },
        )
        assertTrue(
            "the new definition must be live: ${repository.lookupEngine()!!.lookup("食べる").first().entry.definition}",
            repository.lookupEngine()!!.lookup("食べる").first().entry.definition.contains("comer (reinstalado)"),
        )
    }

    @Test
    fun `nothing newer means no update button and an honest summary`() {
        install("jmdict-en", "JMdict", "JMdict (inglês)")
        store(UpdateResult("jmdict-en", UpdateState.CURRENT))

        val texts = labels(activity()).map { it.text.toString() }

        assertFalse("there is nothing to update: $texts", texts.contains("Atualizar"))
        assertTrue(
            "the card should say everything is current: $texts",
            texts.any { it.contains("tudo em dia") },
        )
    }

    @Test
    fun `a dictionary imported from a file explains why it cannot be checked`() {
        install("meu-dicionario", "Meu dicionário", "Meu dicionário")
        store(UpdateResult("meu-dicionario", UpdateState.MANUAL))

        val texts = labels(activity()).map { it.text.toString() }

        assertTrue(
            "an imported dictionary has no publisher to ask: $texts",
            texts.any { it.contains("Importado de um arquivo") },
        )
        assertFalse("there is nothing to update: $texts", texts.contains("Atualizar"))
    }

    @Test
    fun `a stored result for a dictionary that is no longer installed is not offered`() {
        store(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "2026.10.30"))

        val texts = labels(activity()).map { it.text.toString() }

        assertFalse(
            "«Atualizar tudo» must not reinstall a dictionary the user removed: $texts",
            texts.contains("Atualizar tudo"),
        )
    }

    @Test
    fun `the automatic check period is stored and scheduled`() {
        install("jmdict-en", "JMdict", "JMdict (inglês)")
        val activity = activity()

        button(activity, "Desligado")!!.performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val list: ListView = dialog.listView
        assertEquals(
            listOf("Desligado", "Todos os dias", "Toda semana", "Todo mês"),
            (0 until list.count).map { list.adapter.getItem(it).toString() },
        )

        list.performItemClick(null, 2, list.adapter.getItemId(2))

        assertEquals(UpdateInterval.WEEKLY, AppSettings(activity).dictionaryUpdateInterval)
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(DictionaryUpdateScheduler.WORK_NAME)
            .get()
            .single()
        assertEquals(TimeUnit.DAYS.toMillis(7), work.periodicityInfo!!.repeatIntervalMillis)
        assertTrue(
            "the card should show the new choice: ${labels(activity).map { it.text }}",
            labels(activity).any { it.text.toString() == "Toda semana" },
        )
    }

    @Test
    fun `a source recorded at install time survives on the row`() {
        // The check uses this to recognise a downloaded dictionary; a row that lost it would look
        // like an import and never be checked again.
        val directory = File(context.filesDir, "dictionaries/jitendex")
        directory.mkdirs()
        File(directory, "meta.json").writeText(
            """
            {
              "title": "Jitendex",
              "revision": "2026-01-01",
              "format": 3,
              "termCount": 10,
              "kanjiCount": 0,
              "compiledWith": ${DictionaryCompiler.FORMAT_VERSION},
              "sourceName": "Jitendex (inglês)",
              "sourceId": "jitendex",
              "sourceEtag": "\"abc\"",
              "sourceLastModified": "Fri, 30 Oct 2026 10:00:00 GMT",
              "sourceLength": 123
            }
            """.trimIndent(),
        )
        UpdateStateStore(context).save(
            listOf(UpdateResult("jitendex", UpdateState.AVAILABLE, remoteLabel = "30/10/2026")),
            1_700_000_000_000,
        )

        val texts = labels(activity()).map { it.text.toString() }

        assertNotNull("Jitendex is a catalog entry with a publisher", texts.firstOrNull { it == "Atualizar" })
        val item = dev.yabai.ocr.dict.DictionaryRepository.get(context).installed().single()
        assertEquals("jitendex", item.sourceId)
        assertEquals(RemoteSignature("\"abc\"", "Fri, 30 Oct 2026 10:00:00 GMT", 123), item.sourceSignature)
    }

    @Test
    fun `updating one dictionary does not leave the update card frozen`() {
        install("jmdict-en", "JMdict", "JMdict (inglês)", sourceId = "jmdict-en")
        install("kanjidic-en", "KANJIDIC", "KANJIDIC (inglês)", sourceId = "kanjidic-en")
        UpdateStateStore(context).save(
            listOf(
                UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "2026-10-30"),
                UpdateResult("kanjidic-en", UpdateState.AVAILABLE, remoteLabel = "2026-10-30"),
            ),
            1_700_000_000_000,
        )
        val activity = activity()
        activity.remote = FakeRemote(
            archive("2026-10-30", "comer (novo)"),
            RemoteSignature(etag = "\"new\"", length = 123),
        )

        button(activity, "Atualizar")!!.performClick()
        awaitMain { UpdateStateStore(context).load().results["jmdict-en"] == null }

        // The refresh used to run while «busy» was still set, so the rebuilt card computed
        // isEnabled = false and «Verificar»/«Atualizar tudo» stayed dead until the screen was
        // reopened — the device report that started this round (R43).
        val texts = labels(activity).map { it.text.toString() }
        assertTrue("«Verificar» disappeared: $texts", texts.contains("Verificar atualizações"))
        assertTrue(
            "«Verificar» stayed disabled after the update",
            button(activity, "Verificar atualizações")!!.isEnabled,
        )
        val all = button(activity, "Atualizar tudo")
        assertNotNull("the dictionary that was not updated still needs a button: $texts", all)
        assertTrue("«Atualizar tudo» stayed disabled after the update", all!!.isEnabled)
    }

    @Test
    fun `updating all keeps every dictionary on the list and re-enables the card`() {
        install("jmdict-en", "JMdict", "JMdict (inglês)", sourceId = "jmdict-en")
        install("kanjidic-en", "KANJIDIC", "KANJIDIC (inglês)", sourceId = "kanjidic-en")
        UpdateStateStore(context).save(
            listOf(
                UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "2026-10-30"),
                UpdateResult("kanjidic-en", UpdateState.AVAILABLE, remoteLabel = "2026-10-30"),
            ),
            1_700_000_000_000,
        )
        val activity = activity()
        activity.remote = FakeRemote(
            archive("2026-10-30", "comer (novo)"),
            RemoteSignature(etag = "\"new\"", length = 123),
        )

        button(activity, "Atualizar tudo")!!.performClick()
        awaitMain {
            UpdateStateStore(context).load().results.isEmpty() &&
                dev.yabai.ocr.dict.DictionaryRepository.get(context).installed()
                    .all { it.info.revision == "2026-10-30" }
        }

        val repository = dev.yabai.ocr.dict.DictionaryRepository.get(context)
        assertEquals("an in-place update must not remove a dictionary", 2, repository.installed().size)
        val texts = labels(activity).map { it.text.toString() }
        assertFalse(
            "the installed section went empty after «Atualizar tudo»: $texts",
            texts.contains("Nada instalado ainda. Baixe um dicionário abaixo."),
        )
        assertTrue(
            "both dictionaries should still be on screen: $texts",
            texts.count { it == "JMdict" } >= 2,
        )
        assertTrue(
            "«Verificar» stayed disabled after «Atualizar tudo»",
            button(activity, "Verificar atualizações")!!.isEnabled,
        )
    }

    @Test
    fun `the update card shows the download percentage while it runs`() {
        install("jmdict-en", "JMdict", "JMdict (inglês)", sourceId = "jmdict-en")
        store(UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "2026-10-30"))
        val activity = activity()
        val remote = PausingRemote(
            archive("2026-10-30", "comer (novo)"),
            RemoteSignature(etag = "\"new\"", length = 123),
        )
        activity.remote = remote

        button(activity, "Atualizar tudo")!!.performClick()
        ShadowLooper.idleMainLooper()

        val texts = labels(activity).map { it.text.toString() }
        assertTrue(
            "the card only said «Atualizando», without the download's own progress: $texts",
            texts.any { it.contains("Baixando") && it.contains("100%") },
        )

        remote.release.complete(Unit)
        awaitMain { UpdateStateStore(context).load().results.isEmpty() }
    }

    @Test
    fun `progress reported from a background thread is marshalled to the main thread`() {
        // On a device the downloader reports inside withContext(IO); touching a TextView from there
        // throws and aborted the whole update. Robolectric tolerates it, so this test calls the
        // helper from a real background thread and checks where the block actually ran (R43).
        val activity = activity()
        val main = Looper.getMainLooper()
        var ranOnMainLooper: Boolean? = null
        var ranOnThread: Thread? = null

        val worker = Thread {
            activity.onUi {
                ranOnThread = Thread.currentThread()
                ranOnMainLooper = Looper.myLooper() == main
            }
        }
        worker.start()
        worker.join()
        ShadowLooper.idleMainLooper()

        assertEquals("the progress update must not be dropped", true, ranOnMainLooper)
        assertEquals("it must run on the main thread", Thread.currentThread(), ranOnThread)
    }
}
