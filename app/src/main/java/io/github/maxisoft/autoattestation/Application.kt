package io.github.maxisoft.autoattestation

import androidx.multidex.MultiDexApplication
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader


class Application : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}