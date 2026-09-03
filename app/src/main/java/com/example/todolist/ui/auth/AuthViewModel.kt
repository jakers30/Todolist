package com.example.todolist.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolist.AppContainer
import com.example.todolist.data.model.Batch
import com.example.todolist.data.model.Role
import com.example.todolist.ui.common.Event
import com.example.todolist.ui.common.withMinLoading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared across the login / register / forgot-password screens. */
class AuthViewModel(private val container: AppContainer) : ViewModel() {

    val isLoading = MutableLiveData(false)
    val message = MutableLiveData<Event<String>>()
    val onLoggedIn = MutableLiveData<Event<Role>>()
    val onRegistered = MutableLiveData<Event<Unit>>()
    val batches = MutableLiveData<List<Batch>>(emptyList())

    fun loadBatches() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.batchRepository.getBatches()
            }
            batches.value = result
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            message.value = Event("Please enter your username and password.")
            return
        }
        viewModelScope.launch {
            isLoading.withMinLoading {
                val user = withContext(Dispatchers.IO) {
                    container.authRepository.login(username, password)
                }
                if (user != null) {
                    container.sessionManager.saveSession(user.id, user.username, user.role)
                    onLoggedIn.value = Event(user.role)
                } else {
                    message.value = Event("Invalid username or password.")
                }
            }
        }
    }

    fun register(username: String, password: String, confirm: String, batchId: Long) {
        when {
            username.isBlank() -> message.value = Event("Please choose a username.")
            password.length < 6 -> message.value = Event("Password must be at least 6 characters.")
            password != confirm -> message.value = Event("Passwords do not match.")
            // Fix #2: batchId == 0 is the valid "No Batch" choice; only -1 means nothing selected.
            batchId < 0 -> message.value = Event("Please select a batch or choose No Batch.")
            else -> viewModelScope.launch {
                isLoading.withMinLoading {
                    val result = withContext(Dispatchers.IO) {
                        container.authRepository.register(username, password, batchId)
                    }
                    result.fold(
                        onSuccess = {
                            message.value = Event("Account created! Please sign in.")
                            onRegistered.value = Event(Unit)
                        },
                        onFailure = { message.value = Event(it.message ?: "Registration failed.") }
                    )
                }
            }
        }
    }

    fun resetPassword(username: String, newPassword: String, confirm: String) {
        when {
            username.isBlank() -> message.value = Event("Please enter your username.")
            newPassword.length < 6 -> message.value = Event("Password must be at least 6 characters.")
            newPassword != confirm -> message.value = Event("Passwords do not match.")
            else -> viewModelScope.launch {
                isLoading.withMinLoading {
                    val ok = withContext(Dispatchers.IO) {
                        container.authRepository.resetPassword(username, newPassword)
                    }
                    message.value = Event(
                        if (ok) "Password reset. You can sign in now."
                        else "No account found with that username."
                    )
                }
            }
        }
    }

    fun consumeBatches(): LiveData<List<Batch>> = batches
}
