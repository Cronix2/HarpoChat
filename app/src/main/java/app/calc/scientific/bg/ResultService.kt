// SPDX-License-Identifier: MIT
package app.calc.scientific.bg

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.calc.scientific.CalcApp
import app.calc.scientific.MainActivity
import app.calc.scientific.R
import app.calc.scientific.net.CoverSchedule
import app.calc.scientific.net.MeshClient
import app.calc.scientific.security.DropBox
import app.calc.scientific.security.LockState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Réception en arrière-plan (étape N3), quand l'app est verrouillée.
 *
 * Service de premier plan déguisé en calculatrice. Tant que l'app est verrouillée, il garde un
 * lien au mesh, à débit de couverture réduit, et relève les boîtes listées dans la [DropBox].
 * Ce qui arrive est rangé tel quel, toujours chiffré, puis signalé par une notification neutre
 * (« Nouveau résultat disponible »), sans aucun contenu. Dès le déverrouillage, il coupe son
 * lien : la session prend le relais et lit le courrier rangé.
 */
class ResultService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mesh: MeshClient? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels(this)
        val ongoing = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Prête")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(this))
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        ServiceCompat.startForeground(this, ID_ONGOING, ongoing, type)

        val appLock = (application as CalcApp).appLock
        val drop = DropBox(this)
        scope.launch {
            appLock.state.collect { state ->
                when (state) {
                    is LockState.Locked -> startLink(drop)
                    is LockState.Unlocked -> stopLink()
                    LockState.NeedsSetup -> { stopLink(); stopSelf() } // après un effacement
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startLink(drop: DropBox) {
        if (mesh != null) return
        val watches = drop.loadWatches()
        if (watches.isEmpty()) return // aucun contact : pas de trafic inutile
        mesh = MeshClient(scope, downRateHz = CoverSchedule.DOWN_RATE_BACKGROUND_HZ, upRateHz = CoverSchedule.UP_RATE_BACKGROUND_HZ).also { m ->
            m.inbound = { box, blob ->
                drop.append(box, blob)
                notifyResult(this)
            }
            m.setWatches(watches)
            m.start()
        }
    }

    private fun stopLink() {
        mesh?.stop()
        mesh = null
    }

    override fun onDestroy() {
        stopLink()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ONGOING = "calc_ongoing"
        private const val CHANNEL_RESULT = "calc_result"
        private const val ID_ONGOING = 1
        private const val ID_RESULT = 2

        fun start(ctx: Context) = runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ResultService::class.java))
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, ResultService::class.java))

        /** Retire la notification « nouveau résultat » (au déverrouillage). */
        fun clearResult(ctx: Context) =
            (ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(ID_RESULT)

        private fun channels(ctx: Context) {
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, "Calculs en cours", NotificationManager.IMPORTANCE_MIN))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_RESULT, "Résultats", NotificationManager.IMPORTANCE_DEFAULT))
        }

        private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun notifyResult(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val n: Notification = NotificationCompat.Builder(ctx, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText("Nouveau résultat disponible")
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openApp(ctx))
                .build()
            (ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID_RESULT, n)
        }
    }
}
