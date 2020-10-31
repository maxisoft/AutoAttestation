package io.github.maxisoft.autoattestation.attestation.form

import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Okio
import java.io.InputStream
import java.util.*

class FormDataAdapterService(private val data: FormDataAdapter) {
    private val reasonMapping: LinkedHashMap<String, Reason> by lazy {
        LinkedHashMap<String, Reason>(data.reasons.count()).apply {
            data.reasons.forEach {
                if (containsKey(it.id)) error("duplicate key ${it.id}")
                put(it.id, it)
            }
        }
    }

    fun getReason(key: String): Reason {
        return reasonMapping[key] ?: error("there's no key $key")
    }

    fun listReasons(): List<Pair<String, Reason>> {
        return reasonMapping.map { it.key to it.value }
    }

    companion object {
        fun createFromStream(inputStream: InputStream): FormDataAdapterService {
            val adapter = moshi.adapter(FormDataAdapter::class.java)
            JsonReader.of(Okio.buffer(Okio.source(inputStream))).use { buffer ->
                val data = adapter.fromJson(buffer)
                return FormDataAdapterService(data!!)
            }
        }

        val moshi: Moshi by lazy {
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }
    }
}