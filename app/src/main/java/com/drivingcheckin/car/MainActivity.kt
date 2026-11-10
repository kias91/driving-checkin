package com.drivingcheckin.car

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.drivingcheckin.car.ui.AnbuApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        RecorderService.start(this)

        setContent {
            // 차량 화면은 크기와 밀도가 제각각이라, 가로 1120dp 기준 설계를 화면 폭에 맞춰 키운다.
            val widthDp = LocalConfiguration.current.screenWidthDp
            val base = LocalDensity.current
            val scale = (widthDp / 1120f).coerceAtLeast(0.6f)
            CompositionLocalProvider(
                LocalDensity provides Density(base.density * scale, base.fontScale)
            ) {
                AnbuApp(application as App)
            }
        }
    }
}
