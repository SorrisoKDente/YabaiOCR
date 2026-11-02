package dev.yabai.ocr.dict

import android.content.Context
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.yabai.ocr.UpdateInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

/**
 * The automatic check is one piece of periodic work, not one more every time the screen opens (R43).
 *
 * WorkManager survives reboots and process death by itself; what it does not survive is the app
 * asking for the work again with a different policy. «Keep» on app start protects the period the
 * user chose, «cancel and re-enqueue» is only for the moment they actually change it, and «off» has
 * to leave nothing behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryUpdateSchedulerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun work(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(DictionaryUpdateScheduler.WORK_NAME)
            .get()

    @Test
    fun `the chosen period becomes one periodic request with a network constraint`() {
        DictionaryUpdateScheduler.set(context, UpdateInterval.WEEKLY)

        val info = work().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(
            "a weekly choice has to mean seven days",
            TimeUnit.DAYS.toMillis(7),
            info.periodicityInfo!!.repeatIntervalMillis,
        )
        assertEquals(
            "checking while offline only produces failures",
            NetworkType.CONNECTED,
            info.constraints.requiredNetworkType,
        )
    }

    @Test
    fun `changing the period replaces the request instead of stacking a second one`() {
        DictionaryUpdateScheduler.set(context, UpdateInterval.DAILY)
        DictionaryUpdateScheduler.set(context, UpdateInterval.MONTHLY)

        val info = work().single()
        assertEquals(TimeUnit.DAYS.toMillis(30), info.periodicityInfo!!.repeatIntervalMillis)
    }

    @Test
    fun `opening the app keeps the existing period`() {
        DictionaryUpdateScheduler.set(context, UpdateInterval.MONTHLY)

        DictionaryUpdateScheduler.ensure(context, UpdateInterval.WEEKLY)

        assertEquals(
            "«ensure» runs on every app start and must not reset the user's choice",
            TimeUnit.DAYS.toMillis(30),
            work().single().periodicityInfo!!.repeatIntervalMillis,
        )
    }

    @Test
    fun `turning the automatic check off leaves no request behind`() {
        DictionaryUpdateScheduler.set(context, UpdateInterval.WEEKLY)

        DictionaryUpdateScheduler.set(context, UpdateInterval.OFF)

        assertTrue(
            "the work should be cancelled, not left waiting for the next period",
            work().all { it.state == WorkInfo.State.CANCELLED },
        )
    }

    @Test
    fun `off does not create work when the app starts`() {
        DictionaryUpdateScheduler.ensure(context, UpdateInterval.OFF)

        assertTrue("no interval means no background request", work().isEmpty())
    }
}
