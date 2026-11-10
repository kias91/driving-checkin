package com.unjeonanbu.car

import android.app.Application
import com.unjeonanbu.car.data.WeekStore
import com.unjeonanbu.car.vehicle.Recorder
import com.unjeonanbu.car.vehicle.Speaker

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
