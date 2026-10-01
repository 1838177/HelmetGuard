package com.helmet.guard.presentation.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helmet.guard.core.dispatcher.SmsManagerDispatcher
import com.helmet.guard.domain.model.EmergencyContact
import com.helmet.guard.domain.repository.IHelmetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmergencyViewModel(private val repository: IHelmetRepository) : ViewModel() {

    val contacts: StateFlow<List<EmergencyContact>?> = repository.getEmergencyContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveContact(
        name: String,
        phone: String,
        isPrimary: Boolean,
        relationship: String,
        id: Long = 0L,
        isEnabled: Boolean = true
    ) {
        val cleanedPhone = SmsManagerDispatcher.cleanPhoneNumber(phone)
        viewModelScope.launch {
            repository.saveEmergencyContact(
                EmergencyContact(
                    id = id,
                    name = name.trim(),
                    phone = cleanedPhone,
                    isPrimary = isPrimary,
                    relationship = relationship.trim(),
                    isEnabled = isEnabled
                )
            )
        }
    }

    fun toggleContactEnabled(contact: EmergencyContact) {
        viewModelScope.launch {
            repository.updateEmergencyContactEnabled(contact.id, !contact.isEnabled)
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            repository.deleteEmergencyContact(id)
        }
    }
}
