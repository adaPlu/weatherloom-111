package com.rork.weatherloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
