package com.drivingcheckin.car

import android.app.Application
import com.drivingcheckin.car.data.WeekStore
import com.drivingcheckin.car.vehicle.Recorder
import com.drivingcheckin.car.vehicle.Speaker

class App : Application() {
    lateinit var store: WeekStore; private set
    lateinit var recorder: Recorder; private set
    val speaker: Speaker by lazy { Speaker(this) }

    override fun onCreate() {
        super.onCreate()
        store = WeekStore(this)
        recorder = Recorder(this, store)
    }
}
