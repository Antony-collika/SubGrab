            delay(300)
            loadPage(0, replace = true)
        }
    }

    fun setLibraryMode(mode: LibraryMode) {
        debounceJob?.cancel()
        loadJob?.cancel()
        _state.update { it.copy(libraryMode = mode, libraryScope = null, results = emptyList(), libraryObjects = emptyList(), resultCount = 0, page = 0, selectedVideos = emptySet()) }
        if (mode == LibraryMode.VIDEO) search() else loadLibraryObjects(mode)
    }

    fun openLibraryObject(item: com.subgrab.app.domain.LibraryObject) {
        val mode = _state.value.libraryMode
        if (mode == LibraryMode.VIDEO) return
        _state.update { it.copy(libraryScope = LibraryScope(mode, item.id), results = emptyList(), page = 0, loading = true, error = null) }
        loadPage(0, replace = true)
    }

    fun backToLibraryObjects() {
        val mode = _state.value.libraryMode
        if (mode == LibraryMode.VIDEO) return
        _state.update { it.copy(libraryScope = null, results = emptyList(), page = 0, loading = false, selectedVideos = emptySet()) }
        loadLibraryObjects(mode)
    }

    private fun loadLibraryObjects(mode: LibraryMode) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.getLibraryObjects(mode) }
                .onSuccess { objects -> _state.update { it.copy(libraryObjects = objects, loading = false, resultCount = objects.size) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Không thể đọc thư viện") } }
        }