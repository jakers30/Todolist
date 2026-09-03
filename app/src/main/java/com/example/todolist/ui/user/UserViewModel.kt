package com.example.todolist.ui.user

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todolist.AppContainer
import com.example.todolist.data.model.PersonalStats
import com.example.todolist.data.model.Priority
import com.example.todolist.data.model.TaskCard
import com.example.todolist.data.model.TaskSource
import com.example.todolist.data.model.TaskStatus
import com.example.todolist.ui.common.Event
import com.example.todolist.ui.common.withMinLoading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SourceFilter { ALL, BATCH, PERSONAL }

enum class SortBy { DUE_DATE, PRIORITY, STATUS }

/** Fix #6: one day of the weekly bar graph — admin-submitted vs personal completions. */
data class DayCompletion(val dayStart: Long, val general: Int, val personal: Int)

/** Shared by the user fragments (board, personal, progress). */
class UserViewModel(private val container: AppContainer) : ViewModel() {

    val isLoading = MutableLiveData(false)
    val message = MutableLiveData<Event<String>>()

    /** All tasks (batch + personal) before filters. */
    private val allCards = MutableLiveData<List<TaskCard>>(emptyList())

    /** Board list after status/source/sort filters. */
    val boardCards = MutableLiveData<List<TaskCard>>(emptyList())

    val personalCards = MutableLiveData<List<TaskCard>>(emptyList())
    val stats = MutableLiveData<PersonalStats>(PersonalStats(0, 0, 0, 0, 0))
    val dailyCompletions = MutableLiveData<List<DayCompletion>>(emptyList())

    /** Fix #5: admin-submitted (general) vs personal task counts. */
    val sourceBreakdown = MutableLiveData<Pair<Int, Int>>(0 to 0)

    /** Fix #2: true while the user belongs to no batch. */
    val inNoBatch = MutableLiveData(false)
    val availableBatches = MutableLiveData<List<com.example.todolist.data.model.Batch>>(emptyList())

    var statusFilter: TaskStatus? = TaskStatus.ACCEPTED
        private set
    var sourceFilter: SourceFilter = SourceFilter.ALL
        private set
    var sortBy: SortBy = SortBy.DUE_DATE
        private set

    fun loadBoard() {
        val userId = container.sessionManager.userId
        if (userId <= 0) return
        viewModelScope.launch {
            isLoading.withMinLoading {
                val batch = withContext(Dispatchers.IO) {
                    container.taskRepository.getUserTaskCards(userId)
                }
                val personal = withContext(Dispatchers.IO) {
                    container.personalTaskRepository.getPersonalCards(userId)
                }
                allCards.value = batch + personal
                applyFilters()
            }
        }
    }

    fun setStatusFilter(status: TaskStatus?) {
        statusFilter = status
        applyFilters()
    }

    fun setSourceFilter(filter: SourceFilter) {
        sourceFilter = filter
        applyFilters()
    }

    fun setSort(sort: SortBy) {
        sortBy = sort
        applyFilters()
    }

    private fun applyFilters() {
        val all = allCards.value ?: emptyList()
        var result = all
        statusFilter?.let { status -> result = result.filter { it.status == status } }
        result = when (sourceFilter) {
            SourceFilter.ALL -> result
            SourceFilter.BATCH -> result.filter { it.source == TaskSource.BATCH }
            SourceFilter.PERSONAL -> result.filter { it.source == TaskSource.PERSONAL }
        }
        result = when (sortBy) {
            SortBy.DUE_DATE -> result.sortedWith(compareBy<TaskCard> { it.dueDate }.thenBy { it.title })
            SortBy.PRIORITY -> result.sortedByDescending { it.priority.rank }
            SortBy.STATUS -> result.sortedBy { it.status.order }
        }
        boardCards.value = result
    }

    /** Moves a task between board states. attachProof = optional placeholder proof image. */
    fun updateStatus(card: TaskCard, newStatus: TaskStatus, attachProof: Boolean) {
        viewModelScope.launch {
            isLoading.withMinLoading {
                withContext(Dispatchers.IO) {
                    if (card.source == TaskSource.BATCH) {
                        container.taskRepository.updateUserTaskStatus(card.refId, newStatus, attachProof)
                    } else {
                        container.personalTaskRepository.updateStatus(card.refId, newStatus, attachProof)
                    }
                }
                message.value = Event(
                    when (newStatus) {
                        TaskStatus.COMPLETED -> "Marked as completed${if (attachProof) " with proof" else ""}."
                        TaskStatus.ONGOING -> "Moved to Ongoing."
                        TaskStatus.ACCEPTED -> "Moved back to Accepted."
                    }
                )
                loadBoard()
                loadStats()
            }
        }
    }
    // ---------- Personal task space ----------

    fun loadPersonal() {
        val userId = container.sessionManager.userId
        if (userId <= 0) return
        viewModelScope.launch {
            isLoading.withMinLoading {
                val list = withContext(Dispatchers.IO) {
                    container.personalTaskRepository.getPersonalCards(userId)
                }
                personalCards.value = list
            }
        }
    }

    fun createPersonal(title: String, description: String, dueDate: Long, priority: Priority) {
        when {
            title.isBlank() -> message.value = Event("Please add a title.")
            description.isBlank() -> message.value = Event("Please add a description.")
            else -> viewModelScope.launch {
                isLoading.withMinLoading {
                    withContext(Dispatchers.IO) {
                        container.personalTaskRepository.createPersonalTask(
                            container.sessionManager.userId, title, description, dueDate, priority
                        )
                    }
                    message.value = Event("Personal task created.")
                    loadPersonal()
                    loadStats()
                }
            }
        }
    }

    fun deletePersonal(taskId: Long) {
        viewModelScope.launch {
            isLoading.withMinLoading {
                withContext(Dispatchers.IO) {
                    container.personalTaskRepository.deletePersonalTask(taskId)
                }
                message.value = Event("Personal task deleted.")
                loadPersonal()
                loadStats()
            }
        }
    }

    /** Fix #4: save edits to a personal task (UI blocks this for completed tasks). */
    fun updatePersonal(taskId: Long, title: String, description: String, dueDate: Long, priority: Priority) {
        when {
            title.isBlank() -> message.value = Event("Please add a title.")
            description.isBlank() -> message.value = Event("Please add a description.")
            else -> viewModelScope.launch {
                isLoading.withMinLoading {
                    withContext(Dispatchers.IO) {
                        container.personalTaskRepository.updatePersonalTask(taskId, title, description, dueDate, priority)
                    }
                    message.value = Event("Personal task updated.")
                    loadPersonal()
                    loadStats()
                }
            }
        }
    }

    // ---------- Progress ----------

    fun loadStats() {
        val userId = container.sessionManager.userId
        if (userId <= 0) return
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) {
                container.personalTaskRepository.getPersonalStats(userId)
            }
            val personalDaily = withContext(Dispatchers.IO) {
                container.personalTaskRepository.completedPerDayOfWeek(userId)
            }
            val generalDaily = withContext(Dispatchers.IO) {
                container.taskRepository.completedPerDayOfWeek(userId)
            }
            val generalCount = withContext(Dispatchers.IO) {
                container.taskRepository.countUserBatchTasks(userId)
            }
            stats.value = s
            // Fix #5: (general, personal) split.
            sourceBreakdown.value = generalCount to s.total
            // Fix #6: join the two daily series into one graph.
            dailyCompletions.value = personalDaily.zip(generalDaily) { personal, general ->
                DayCompletion(personal.first, general.second, personal.second)
            }
        }
    }

    // ---------- Fix #2: No Batch ----------

    fun loadBatchState() {
        val userId = container.sessionManager.userId
        if (userId <= 0) return
        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) {
                container.authRepository.getUserById(userId)
            }
            inNoBatch.value = user != null && user.batchId == null
        }
    }

    fun loadAvailableBatches() {
        viewModelScope.launch {
            availableBatches.value = withContext(Dispatchers.IO) {
                container.batchRepository.getBatches()
            }
        }
    }

    /** Fix #2: assign the user to the selected batch (grants its existing tasks). */
    fun joinBatch(batchId: Long) {
        viewModelScope.launch {
            isLoading.withMinLoading {
                withContext(Dispatchers.IO) {
                    container.authRepository.updateUserBatch(container.sessionManager.userId, batchId)
                }
                message.value = Event("Joined batch.")
                inNoBatch.value = false
                loadBoard()
                loadStats()
            }
        }
    }
}
