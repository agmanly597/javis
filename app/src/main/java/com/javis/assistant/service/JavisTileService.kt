package com.javis.assistant.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.javis.assistant.MainActivity

/**
 * Quick Settings Tile — user drags this into their notification shade.
 * One tap activates JAVIS voice from ANYWHERE on the phone — no overlay needed.
 *
 * To add: Pull down notification shade → Edit (pencil icon) → drag "JAVIS" tile into active tiles.
 */
@RequiresApi(Build.VERSION_CODES.N)
class JavisTileService : TileService() {

    override fun onTileAdded() {
        updateTile(active = false)
    }

    override fun onStartListening() {
        updateTile(active = false)
    }

    override fun onClick() {
        // Collapse quick panel and open JAVIS with voice immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ — use startActivityAndCollapse with PendingIntent
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).apply {
                    action = JavisBackgroundService.ACTION_ACTIVATE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).apply {
                    action = JavisBackgroundService.ACTION_ACTIVATE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        }
        updateTile(active = true)
    }

    private fun updateTile(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "JAVIS"
            contentDescription = "Activate JAVIS voice assistant"
            updateTile()
        }
    }
}
