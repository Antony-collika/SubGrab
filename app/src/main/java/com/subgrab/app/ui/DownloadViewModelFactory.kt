package com.subgrab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.subgrab.app.data.YtDlpRunner

class DownloadViewModelFactory(private val runner: YtDlpRunner?) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = DownloadViewModel(runner) as T
}
