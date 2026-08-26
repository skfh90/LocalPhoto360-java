package com.localphoto360.app.data

import android.net.Uri

data class SpherePhoto(
    val id: String,
    val displayName: String,
    val createdAt: Long,
    val isPhotosphere: Boolean,
    val isSample: Boolean,
    val uri: Uri,
    val filePath: String?,
    val width: Int,
    val height: Int,
)
