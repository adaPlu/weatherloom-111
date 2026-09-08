package com.rork.weatherloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.weatherloom.audio.LoomAudio
import com.rork.weatherloom.core.level.LevelLibrary
import com.rork.weatherloom.data.GameRepository
import com.rork.weatherloom.ui.navigation.AppNavigation
import com.rork.weatherloom.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LevelLibrary.load(applicationContext)
        LoomAudio.init(applicationContext)
        val repo = GameRepository.get(applicationContext)
        val initialSave = repo.save.value
        LoomAudio.setSfxEnabled(initialSave.soundEnabled)
        LoomAudio.setMusicEnabled(initialSave.musicEnabled)
        setContent {
            val save by repo.save.collectAsStateWithLifecycle()
            AppTheme(highContrast = save.highContrast) {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LoomAudio.enterForeground()
    }

    override fun onPause() {
        LoomAudio.enterBackground()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) LoomAudio.release()
        super.onDestroy()
    }
}
