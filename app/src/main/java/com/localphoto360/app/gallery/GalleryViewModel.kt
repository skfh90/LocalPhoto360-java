package com.localphoto360.app.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localphoto360.app.data.PhotoRepository
import com.localphoto360.app.data.SpherePhoto
import com.localphoto360.app.photoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryUiState(
    val photos: List<SpherePhoto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: PhotoRepository = application.photoRepository
    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { repo.list() }
            }.onSuccess { photos ->
                _state.value = _state.value.copy(photos = photos, loading = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: "Could not load photos.",
                )
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repo.importFrom(uri) }
            }.onSuccess { photo ->
                refresh()
                _state.value = _state.value.copy(message = "Imported ${photo.displayName}")
            }.onFailure { error ->
                _state.value = _state.value.copy(error = error.message ?: "Import failed.")
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            refresh()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
