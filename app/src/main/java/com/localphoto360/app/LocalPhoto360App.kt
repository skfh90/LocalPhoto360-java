package com.localphoto360.app

import android.app.Application
import android.content.Context
import com.localphoto360.app.data.PhotoRepository

class LocalPhoto360App : Application() {
    lateinit var photos: PhotoRepository
        private set

    override fun onCreate() {
        super.onCreate()
        photos = PhotoRepository(this)
    }
}

val Context.photoRepository: PhotoRepository
    get() = (applicationContext as LocalPhoto360App).photos
