package com.example.todolist.ui.admin

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolist.AppContainer
import com.example.todolist.data.model.Batch
import com.example.todolist.data.model.BatchProgressStat
import com.example.todolist.data.model.BatchTask
import com.example.todolist.data.model.Priority
import com.example.todolist.data.model.TaskCompletionStat
import com.example.todolist.ui.common.Event
import com.example.todolist.ui.common.withMinLoading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdminTaskUi(val task: BatchTask, val batchName: String)

/** Shared by the admin fragments (tasks, form, batches, progress). */
class AdminViewModel(private val container: AppContainer) : ViewModel() {

    val isLoading = MutableLiveData(false)
    val message = MutableLiveData<Event<String>>()

    val tasks = MutableLiveData<List<AdminTaskUi>>(emptyList())
    val batches = MutableLiveData<List<Batch>>(emptyList())
    val taskStats = MutableLiveData<List<TaskCompletionStat>>(emptyList())
    val batchStats = MutableLiveData<List<BatchProgressStat>>(emptyList())
    val editingTask = MutableLiveData<BatchTask?>(null)
    val onSaved = MutableLiveData<Event<Unit>>()

    /** Fix #4: active batch filter on the Task tab. null = show all batches. */
    val batchFilterId = MutableLiveData<Long?>(null)

    fun loadTasks() {
        viewModelScope.launch {
            isLoading.withMinLoading {
                val filter = batchFilterId.value
                val list = withContext(Dispatchers.IO) {
                    container.taskRepository.getBatchTasks()
                        .filter { filter == null || it.batchId == filter }
                        .map { task ->
                            AdminTaskUi(
                                task,
                                container.batchRepository.getBatchName(task.batchId) ?: "Unknown"
                            )
                        }
                }
                tasks.value = list
            }
        }
    }

    /** Fix #4: tap the filter chip to cycle All → batch1 → batch2 → … → All. */
    fun cycleBatchFilter() {
        val all = batches.value ?: emptyList()
        if (all.isEmpty()) return
        val current = batchFilterId.value
        val next = when {
            current == null -> all.first().id
            else -> {
                val idx = all.indexOfFirst { it.id == current }
                if (idx in 0 until all.size - 1) all[idx + 1].id else null
            }
        }
        batchFilterId.value = next
        loadTasks()
    }

    fun loadBatches() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { container.batchRepository.getBatches() }
            batches.value = list
            // Fix #4: if the active filter's batch no longer exists (e.g. deleted),
            // reset the filter so the task list refreshes to "All batches".
            val filter = batchFilterId.value
            if (filter != null && list.none { it.id == filter }) {
                batchFilterId.value = null
                loadTasks()
            }
        }
    }

    fun loadProgress() {
        viewModelScope.launch {
            isLoading.withMinLoading {
                val stats = withContext(Dispatchers.IO) {
                    container.taskRepository.getTaskCompletionStats()
                }
                val batch = withContext(Dispatchers.IO) {
                    container.taskRepository.getBatchProgressStats()
                }
                taskStats.value = stats
                batchStats.value = batch
            }
        }
    }

    fun loadTaskForEdit(taskId: Long) {
        viewModelScope.launch {
            val task = withContext(Dispatchers.IO) {
                container.taskRepository.getBatchTask(taskId)
            }
            editingTask.value = task
        }
    }

    fun saveTask(taskId: Long, title: String, description: String, dueDate: Long, priority: Priority, batchId: Long) {
        when {
            title.isBlank() -> message.value = Event("Please add a title.")
            description.isBlank() -> message.value = Event("Please add a description.")
            batchId <= 0 -> message.value = Event("Please select a batch.")
            else -> viewModelScope.launch {
                isLoading.withMinLoading {
                    withContext(Dispatchers.IO) {
                        if (taskId > 0) {
                            container.taskRepository.updateBatchTask(taskId, title, description, dueDate, priority)
                        } else {
                            container.taskRepository.createBatchTask(title, description, dueDate, priority, batchId)
                        }
                    }
                    message.value = Event(if (taskId > 0) "Task updated." else "Task sent to batch.")
                    loadTasks()
                    onSaved.value = Event(Unit)
                }
            }
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            isLoading.withMinLoading {
                withContext(Dispatchers.IO) {
                    container.taskRepository.deleteBatchTask(taskId)
                }
                message.value = Event("Task deleted from all users' views.")
                loadTasks()
            }
        }
    }

    fun createBatch(name: String) {
        viewModelScope.launch {
            isLoading.withMinLoading {
                val result = withContext(Dispatchers.IO) {
                    container.batchRepository.createBatch(name)
                }
                result.fold(
                    onSuccess = {
                        message.value = Event("Batch \"${it.name}\" created.")
                        loadBatches()
                    },
                    onFailure = { message.value = Event(it.message ?: "Could not create batch.") }
                )
            }
        }
    }

    /** Fix #1: delete a batch — tasks vanish from members' views, members → No Batch. */
    fun deleteBatch(batchId: Long) {
        viewModelScope.launch {
            isLoading.withMinLoading {
                withContext(Dispatchers.IO) {
                    container.batchRepository.deleteBatch(batchId)
                }
                message.value = Event("Batch deleted. Members moved to No Batch.")
                loadBatches()
                loadTasks()
                loadProgress()
            }
        }
    }
}
