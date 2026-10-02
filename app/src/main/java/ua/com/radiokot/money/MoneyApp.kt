/* Copyright 2025 Oleg Koretsky

   This file is part of the 4Money,
   a budget tracking Android app.

   4Money is free software: you can redistribute it
   and/or modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation, either version 3 of the License,
   or (at your option) any later version.

   4Money is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
   See the GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with 4Money. If not, see <http://www.gnu.org/licenses/>.
*/

package ua.com.radiokot.money

import android.app.Application
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import com.composeunstyled.ComposeUnstyledFlags
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import ua.com.radiokot.money.auth.authModule
import ua.com.radiokot.money.auth.data.UserSession
import ua.com.radiokot.money.auth.logic.DI_SCOPE_SESSION
import ua.com.radiokot.money.auth.logic.UserSessionHolder
import ua.com.radiokot.money.currency.logic.CurrencyPricesUpdateWorker
import ua.com.radiokot.money.home.homeModule
import ua.com.radiokot.money.lock.appLockModule
import ua.com.radiokot.money.lock.logic.AppLock
import ua.com.radiokot.money.powersync.BackgroundPowerSyncWorker
import ua.com.radiokot.money.powersync.PowerSyncConnection
import ua.com.radiokot.money.theme.data.ThemePreferences
import ua.com.radiokot.money.theme.themeModule
import ua.com.radiokot.money.util.KermitSlf4jLogWriter
import ua.com.radiokot.money.util.KoinSlf4jLogger
import java.io.File
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class MoneyApp : Application() {

    private val log by lazy {
        // Logger with a static name.
        // Must be lazy so it is not created before initLogging()
        KotlinLogging.logger("App")
    }

    private val themeScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // Make Text(style = TextStyle(color = ...)) keep its color
        // even though MoneyTheme provides LocalContentColor.
        ComposeUnstyledFlags.strictTextColorResolutionOrder = true

        initLogging()

        startKoin {
            logger(KoinSlf4jLogger)
            androidContext(this@MoneyApp)

            modules(
                themeModule,
                authModule,
                appLockModule,
                homeModule,
            )
        }

        initTheme()
        initSessionHolder()
        initBackgroundSync()
        initCurrencyPricesUpdate()
        initLock()
        initSyncConnectionLifecycle()
    }

    private fun initTheme() {
        val themePreferences: ThemePreferences = get()

        AppCompatDelegate.setDefaultNightMode(
            themePreferences.themeMode.value.appCompatNightMode
        )

        // AppCompat recreates started activities when the mode changes.
        themeScope.launch {
            themePreferences.themeMode
                .drop(1)
                .collect { themeMode ->
                    log.debug {
                        "initTheme(): applying theme mode:" +
                                "\nthemeMode=$themeMode"
                    }

                    AppCompatDelegate.setDefaultNightMode(themeMode.appCompatNightMode)
                }
        }
    }

    private fun initLogging() {
        // The Logback configuration is in the app/src/main/assets/logback.xml

        @Suppress("KotlinConstantConditions", "RedundantSuppression")
        System.setProperty(
            "LOG_LEVEL",
            if (BuildConfig.DEBUG)
                "TRACE"
            else
                "INFO"
        )

        try {
            val logFolder =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    getString(R.string.app_name)
                )
                    .also(File::mkdirs)

            System.setProperty(
                "LOG_FILE_DIRECTORY",
                logFolder.path
            )
        } catch (e: Exception) {
            log.error(e) {
                "initLogging(): failed log file folder initialization"
            }
        }

        val defaultUncaughtExceptionHandler: UncaughtExceptionHandler? =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            log.error(exception) { "Fatal exception\n" }

            if (defaultUncaughtExceptionHandler != null) {
                defaultUncaughtExceptionHandler.uncaughtException(thread, exception)
            } else {
                exitProcess(10)
            }
        }

        // Global Kermit.
        Logger.setLogWriters(KermitSlf4jLogWriter)

        log.trace {
            "initLogging(): trace logger enabled"
        }
        log.debug {
            "initLogging(): debug logger enabled"
        }
        log.info {
            "initLogging(): info logger enabled"
        }
    }

    private fun initSessionHolder() {

        val supabaseSession = runBlocking {
            get<SupabaseClient>().run {
                auth.loadFromStorage(
                    autoRefresh = false,
                )
                auth.currentSessionOrNull()
            }
        }

        if (supabaseSession == null) {
            log.info {
                "There is no user session"
            }

            log.debug {
                "initSessionHolder(): no stored session found"
            }

            return
        }

        log.info {
            "Loaded session for user '${supabaseSession.user?.id}' " +
                    "expiring at ${supabaseSession.expiresAt.toLocalDateTime(TimeZone.currentSystemDefault())}"
        }

        log.debug {
            "initSessionHolder(): setting the loaded session:" +
                    "\nuserId=${supabaseSession.user?.id}," +
                    "\nexpiresAt=${supabaseSession.expiresAt}"
        }

        get<UserSessionHolder>().set(
            UserSession(
                supabaseUserSession = supabaseSession,
            )
        )
    }

    private fun initBackgroundSync() {

        val workRequest =
            PeriodicWorkRequestBuilder<BackgroundPowerSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkRequest(
                            networkRequest = NetworkRequest.Builder()
                                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                .build(),
                            networkType = NetworkType.CONNECTED,
                        )
                        .build()
                )
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "BackgroundSync",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
    }

    private fun initCurrencyPricesUpdate() {

        val workRequest =
            PeriodicWorkRequestBuilder<CurrencyPricesUpdateWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkRequest(
                            networkRequest = NetworkRequest.Builder()
                                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                .build(),
                            networkType = NetworkType.CONNECTED,
                        )
                        .build()
                )
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "CurrencyPricesUpdate",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
    }

    private fun initLock() {

        val lock: AppLock = get()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onStart(owner: LifecycleOwner) {
                lock.onAppReturnedToForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                lock.onAppWentToBackground()
            }
        })
    }

    private fun initSyncConnectionLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {

            // Connected in MoneyAppActivity.onStart().
            override fun onStop(owner: LifecycleOwner) {
                getKoin()
                    .getScopeOrNull(DI_SCOPE_SESSION)
                    ?.getOrNull<PowerSyncConnection>()
                    ?.disconnectWhenIdle(PowerSyncConnection.Holder.VISIBLE_APP)
            }
        })
    }
}
