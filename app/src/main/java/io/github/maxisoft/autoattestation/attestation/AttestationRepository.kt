package io.github.maxisoft.autoattestation.attestation

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import io.github.maxisoft.autoattestation.BuildConfig
import java.io.File
import java.io.OutputStream

class AttestationRepository(val context: Context) {
    private val pdfDir: File by lazy {
        context.getDir("pdf", Context.MODE_PRIVATE)
    }

    val latestPdfFile: File by lazy {
        File(context.cacheDir, "latest.pdf")
    }

    private fun genPdfFilename(attestationSettings: AttestationSettings): File {
        return File(pdfDir, datetimeFileFormat.format(attestationSettings.leavingDate) + ".pdf")
    }

    val hasLatestPdf: Boolean
        get() = latestPdfFile.exists()

    fun save(pdf: ByteArray, attestationSettings: AttestationSettings): Uri {
        genPdfFilename(attestationSettings).let { outputFile ->
            File(outputFile.parent!!).let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }

            if (BuildConfig.DEBUG) {
                outputFile.outputStream().use {
                    it.write(pdf)
                }
            }


            latestPdfFile.outputStream().use {
                it.write(pdf)
            }


            return outputFile.toUri()
        }
    }

    fun saveInto(
        pdf: ByteArray,
        attestationSettings: AttestationSettings,
        outputStream: OutputStream,
        cache: Boolean = true
    ) {

        outputStream.write(pdf)

        if (cache) {
            latestPdfFile.outputStream().use {
                it.write(pdf)
            }
        }
    }
}