package io.github.refael302.beamfinder.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Receives PackageInstaller session status; launches the system confirmation UI when needed. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure {
                        if (sessionId >= 0) {
                            InstallStatusHub.complete(
                                sessionId,
                                InstallStatusHub.Status(
                                    PackageInstaller.STATUS_FAILURE,
                                    it.message ?: "Could not open install confirmation",
                                ),
                            )
                        }
                    }
            } else if (sessionId >= 0) {
                InstallStatusHub.complete(
                    sessionId,
                    InstallStatusHub.Status(
                        PackageInstaller.STATUS_FAILURE,
                        "Missing install confirmation intent",
                    ),
                )
            }
            // Keep waiting for the final SUCCESS / FAILURE after the user acts.
            return
        }

        if (sessionId >= 0) {
            InstallStatusHub.complete(sessionId, InstallStatusHub.Status(status, message))
        }
    }
}
