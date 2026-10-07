package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.CloudFile
import kotlinx.coroutines.CancellationException

internal data class FileBatchResult(
    val completed: List<CloudFile>,
    val failures: List<Pair<CloudFile, Exception>>,
)

internal fun performFileBatch(files: List<CloudFile>, operation: (CloudFile) -> Unit): FileBatchResult {
    val completed = mutableListOf<CloudFile>()
    val failures = mutableListOf<Pair<CloudFile, Exception>>()
    for (file in files) {
        try {
            operation(file)
            completed += file
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            failures += file to failure
        }
    }
    return FileBatchResult(completed, failures)
}

internal fun canPasteFiles(files: List<CloudFile>, destination: String): Boolean = files.isNotEmpty() && files.all { file ->
    destination != file.path.substringBeforeLast('/', "") &&
        !(file.isFolder && (destination == file.path || destination.startsWith("${file.path}/")))
}
