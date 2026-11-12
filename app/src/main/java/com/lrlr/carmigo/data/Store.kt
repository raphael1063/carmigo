package com.lrlr.carmigo.data

import android.content.Context
import android.util.Log
import com.lrlr.carmigo.core.AppState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/**
 * 차 안 저장소. 앱 전용 폴더의 JSON 파일 하나이고, 차 밖으로 나가지 않는다.
 * 쓰기는 임시 파일을 거쳐 통째로 바꿔서, 시동이 꺼지는 순간에도 반쯤 쓴 파일이 남지 않게 한다.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "days.json")
    private val tmp = File(context.filesDir, "days.json.tmp")
    /** 바로 앞 세대. 전원이 끊겨 새 파일이 깨졌을 때 읽는다. */
    private val bak = File(context.filesDir, "days.json.bak")

    fun load(): AppState {
        if (!file.exists() && !bak.exists()) return AppState()
        read(file)?.let { return it }
        Log.w(TAG, "state unreadable, trying the previous generation")
        // 읽지 못한 파일은 덮어쓰지 않고 옆에 남겨 둔다
        runCatching { if (file.exists()) file.copyTo(File(file.parentFile, "days.unreadable.json"), overwrite = true) }
        return read(bak) ?: AppState()
    }

    private fun read(f: File): AppState? = try {
        if (f.exists() && f.length() > 0) AppJson.decodeFromString<AppState>(f.readText()) else null
    } catch (e: Exception) {
        Log.w(TAG, "${f.name} unreadable", e)
        null
    }

    @Synchronized
    fun save(state: AppState) {
        try {
            val bytes = AppJson.encodeToString(state).toByteArray(Charsets.UTF_8)
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync() // 이름을 바꾸기 전에 디스크까지 내려 쓴다
            }
            if (read(file) != null) file.copyTo(bak, overwrite = true)
            if (!tmp.renameTo(file)) {
                file.writeBytes(bytes)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "save failed", e)
        }
    }

    /** 기록을 지운 뒤에는 읽지 못해 옆에 두었던 파일도 남기지 않는다. */
    fun clearLeftovers() {
        runCatching { File(file.parentFile, "days.unreadable.json").delete() }
        runCatching { bak.delete() }
    }

    private companion object { const val TAG = "DaysStore" }
}
