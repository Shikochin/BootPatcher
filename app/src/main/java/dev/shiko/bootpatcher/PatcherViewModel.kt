package dev.shiko.bootpatcher

import android.app.Application
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long?,
)

data class OutputDirectory(
    val uri: Uri,
    val name: String,
)

enum class OperationState {
    IDLE,
    PROCESSING,
    SUCCESS,
    ERROR,
}

data class PatcherUiState(
    val boot: PickedFile? = null,
    val kernel: PickedFile? = null,
    val outputDirectory: OutputDirectory? = null,
    val outputFileName: String = DEFAULT_OUTPUT_FILE_NAME,
    val operation: OperationState = OperationState.IDLE,
    val stage: PatchStage? = null,
    val error: String? = null,
) {
    val canPatch: Boolean
        get() = boot != null &&
            kernel != null &&
            outputDirectory != null &&
            operation != OperationState.PROCESSING
}

class PatcherViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, 0)
    private val _state = MutableStateFlow(
        PatcherUiState(
            outputDirectory = loadOutputDirectory(),
            outputFileName = preferences.getString(
                KEY_OUTPUT_FILE_NAME,
                DEFAULT_OUTPUT_FILE_NAME,
            ) ?: DEFAULT_OUTPUT_FILE_NAME,
        ),
    )
    val state: StateFlow<PatcherUiState> = _state.asStateFlow()

    fun selectBoot(uri: Uri) = select(uri) { file ->
        copy(boot = file, operation = OperationState.IDLE, error = null)
    }

    fun selectKernel(uri: Uri) = select(uri) { file ->
        copy(kernel = file, operation = OperationState.IDLE, error = null)
    }

    fun selectOutputDirectory(uri: Uri) {
        if (_state.value.operation == OperationState.PROCESSING) return
        val application = getApplication<Application>()
        viewModelScope.launch {
            runCatching {
                application.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                withContext(Dispatchers.IO) {
                    OutputDirectory(uri, OutputDocumentResolver.queryDirectoryName(application, uri))
                }
            }.onSuccess { directory ->
                preferences.edit()
                    .putString(KEY_OUTPUT_DIRECTORY_URI, directory.uri.toString())
                    .putString(KEY_OUTPUT_DIRECTORY_NAME, directory.name)
                    .apply()
                _state.update {
                    it.copy(
                        outputDirectory = directory,
                        operation = OperationState.IDLE,
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                showError(throwable)
            }
        }
    }

    fun updateOutputFileName(value: String) {
        if (_state.value.operation == OperationState.PROCESSING) return
        _state.update { it.copy(outputFileName = value, operation = OperationState.IDLE, error = null) }
        preferences.edit().putString(KEY_OUTPUT_FILE_NAME, value).apply()
    }

    fun normalizeOutputFileName() {
        val normalized = normalizeOutputFileName(_state.value.outputFileName)
        _state.update { it.copy(outputFileName = normalized) }
        preferences.edit().putString(KEY_OUTPUT_FILE_NAME, normalized).apply()
    }

    fun patch() {
        val snapshot = _state.value
        val boot = snapshot.boot ?: return
        val kernel = snapshot.kernel ?: return
        val outputDirectory = snapshot.outputDirectory ?: return
        val outputFileName = normalizeOutputFileName(snapshot.outputFileName)
        if (snapshot.operation == OperationState.PROCESSING) return

        _state.update {
            it.copy(operation = OperationState.PROCESSING, stage = PatchStage.COPYING_BOOT, error = null)
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val application = getApplication<Application>()
                    BootPatcher(application).patch(
                        bootUri = boot.uri,
                        kernelUri = kernel.uri,
                        kernelName = kernel.name,
                        outputUriProvider = {
                            val outputUri = OutputDocumentResolver.getOrCreate(
                                application,
                                outputDirectory.uri,
                                outputFileName,
                            )
                            check(
                                !OutputDocumentResolver.isSameDocument(
                                    application,
                                    outputUri,
                                    boot.uri,
                                ) && !OutputDocumentResolver.isSameDocument(
                                    application,
                                    outputUri,
                                    kernel.uri,
                                ),
                            ) { application.getString(R.string.output_conflict) }
                            outputUri
                        },
                        onStage = { stage -> _state.update { it.copy(stage = stage) } },
                    )
                }
            }.onSuccess {
                preferences.edit().putString(KEY_OUTPUT_FILE_NAME, outputFileName).apply()
                _state.update {
                    it.copy(
                        outputFileName = outputFileName,
                        operation = OperationState.SUCCESS,
                        stage = null,
                    )
                }
            }.onFailure(::showError)
        }
    }

    fun dismissResult() {
        _state.update { it.copy(operation = OperationState.IDLE, error = null) }
    }

    private fun select(uri: Uri, update: PatcherUiState.(PickedFile) -> PatcherUiState) {
        if (_state.value.operation == OperationState.PROCESSING) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { queryFile(uri) }
            }.onSuccess { file ->
                _state.update { current -> current.update(file) }
            }.onFailure(::showError)
        }
    }

    private fun queryFile(uri: Uri): PickedFile {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "image"
        var size: Long? = null
        getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor: Cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return PickedFile(uri, name, size)
    }

    private fun loadOutputDirectory(): OutputDirectory? {
        val uri = preferences.getString(KEY_OUTPUT_DIRECTORY_URI, null)?.let(Uri::parse)
            ?: return null
        val name = preferences.getString(KEY_OUTPUT_DIRECTORY_NAME, null)
            ?: uri.lastPathSegment?.substringAfterLast(':').orEmpty()
        return OutputDirectory(uri, name)
    }

    private fun showError(throwable: Throwable) {
        _state.update {
            it.copy(
                operation = OperationState.ERROR,
                stage = null,
                error = throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "boot_patcher_preferences"
        const val KEY_OUTPUT_DIRECTORY_URI = "output_directory_uri"
        const val KEY_OUTPUT_DIRECTORY_NAME = "output_directory_name"
        const val KEY_OUTPUT_FILE_NAME = "output_file_name"
    }
}
