package com.ap.weatherloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ap.weatherloom.audio.LoomAudio
import com.ap.weatherloom.core.level.LevelLibrary
import com.ap.weatherloom.data.GameRepository
import com.ap.weatherloom.ui.navigation.AppNavigation
import com.ap.weatherloom.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LevelLibrary.load(applicationContext)
        LoomAudio.init(applicationContext)
        val save = GameRepository.get(applicationContext).save.value
        LoomAudio.setSfxEnabled(save.soundEnabled)
        LoomAudio.setMusicEnabled(save.musicEnabled)
        setContent {
            AppTheme {
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
