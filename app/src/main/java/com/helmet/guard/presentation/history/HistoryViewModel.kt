package com.helmet.guard.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helmet.guard.domain.model.AccidentRecord
import com.helmet.guard.domain.model.DeviceLog
import com.helmet.guard.domain.repository.IHelmetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: IHelmetRepository) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0) // 0: 事故历史, 1: 系统与连接日志
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val accidentRecords: StateFlow<List<AccidentRecord>> = repository.getAccidentRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceLogs: StateFlow<List<DeviceLog>> = repository.getDeviceLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
