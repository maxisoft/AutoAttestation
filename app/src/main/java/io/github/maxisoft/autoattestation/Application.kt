package io.github.maxisoft.autoattestation

import androidx.multidex.MultiDexApplication
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import java.util.*


class Application : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }

    companion object {
        val moshi: Moshi by lazy {
            Moshi.Builder()
                .add(Date::class.java, Rfc3339DateJsonAdapter())
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }
    }
}