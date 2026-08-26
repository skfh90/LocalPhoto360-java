package com.localphoto360.app

import android.app.Application
import android.content.Context
import com.localphoto360.app.data.PhotoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LocalPhoto360App : Application() {
    lateinit var photos: PhotoRepository
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        photos = PhotoRepository(this)
    }
}

val Context.photoRepository: PhotoRepository
    get() = (applicationContext as LocalPhoto360App).photos

val Context.appScope: CoroutineScope
    get() = (applicationContext as LocalPhoto360App).applicationScope
