package home

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class HomeScreenModel : ViewModel() {
    val uiState = mutableStateOf(UiState())

    init {
        uiState.value = uiState.value.copy(message = "")
    }

    data class UiState(
        val showContent: Boolean = false,
        val message: String = ""
    )
}
