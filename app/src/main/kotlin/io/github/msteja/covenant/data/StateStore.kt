package io.github.msteja.covenant.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    allowStructuredMapKeys = true
}

private object AppStateSerializer : Serializer<AppState> {
    override val defaultValue: AppState = AppState(commitments = Starter.commitments())

    override suspend fun readFrom(input: InputStream): AppState = try {
        json.decodeFromString(AppState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Covenant's state can't be read", e)
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(json.encodeToString(AppState.serializer(), t).encodeToByteArray())
    }
}

/** The one place Covenant's state lives: a JSON file in app storage, never backed up (R48, R50). */
class StateStore(context: Context, private val scope: CoroutineScope) {
    private val store: DataStore<AppState> = DataStoreFactory.create(
        serializer = AppStateSerializer,
        scope = scope,
        produceFile = { context.filesDir.resolve("covenant.json") },
    )

    private val loaded = MutableStateFlow<AppState?>(null)

    /** Null until the file has been read once. */
    val state: StateFlow<AppState?> = loaded.asStateFlow()

    /** The latest state, or the defaults before the first read. */
    val current: AppState get() = loaded.value ?: AppStateSerializer.defaultValue

    init {
        scope.launch {
            store.data.collect { value ->
                loaded.value = if (value.createdAt == Instant.EPOCH) value.copy(createdAt = Instant.now()) else value
            }
        }
    }

    suspend fun update(change: (AppState) -> AppState): AppState = store.updateData(change)

    fun launchUpdate(change: (AppState) -> AppState) {
        scope.launch { update(change) }
    }
}
