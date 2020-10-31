package io.github.maxisoft.autoattestation.attestation

import android.util.Log
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import io.github.maxisoft.autoattestation.attestation.form.FormDataAdapterService
import java.io.ByteArrayOutputStream
import java.io.InputStream


class AttestationGenerator(
    private val settings: AttestationSettings,
    private val formDataAdapterService: FormDataAdapterService
) {

    fun generate(buff: InputStream): ByteArray {
        PDDocument.load(buff).use { pdf ->

            updateDocumentInformation(pdf)
            fillFirstPage(pdf)
            printQRCode(pdf)

            ByteArrayOutputStream().use {
                pdf.save(it)
                return it.toByteArray()
            }
        }

    }

    private fun fillFirstPage(
        pdf: PDDocument
    ) {
        val page1 = pdf.pages.first()

        fun drawText(text: String, x: Float, y: Float, size: Float = 11f) {
            PDPageContentStream(pdf, page1, true, true, true).use {
                val font: PDFont = PDType1Font.HELVETICA
                it.beginText()
                it.setFont(font, size)
                it.newLineAtOffset(x, y)
                it.showText(text)
                it.endText()
            }
        }

        drawText("${settings.firstName} ${settings.lastName}", 119f, 696f)
        drawText(settings.formattedBirthDay, 119f, 674f)
        drawText(settings.lieuNaissance, 297f, 674f)
        drawText("${settings.address} ${settings.zipCode} ${settings.city}", 133f, 652f)
        val locationSize = getIdealFontSize(settings.city, 83f, 7f, 11f)
        drawText(settings.city, 105f, 177f, locationSize ?: 7f)
        drawText(settings.formattedDateSortie, 91f, 153f, 11f)
        drawText(settings.formattedHeureSortie, 264f, 153f, 11f)

        settings.reasons.forEach { reason ->
            drawText("x", 78f, reason.pdfOffsetY, 18f)
        }
    }


    private fun updateDocumentInformation(pdf: PDDocument) {
        pdf.documentInformation.title = "COVID-19 - Déclaration de déplacement"
        pdf.documentInformation.subject = "Attestation de déplacement dérogatoire"
        pdf.documentInformation.keywords = arrayOf(
            "covid19",
            "covid-19",
            "attestation",
            "déclaration",
            "déplacement",
            "officielle",
            "gouvernement"
        ).joinToString(",")
        pdf.documentInformation.producer = "DNUM/SDIT"
        pdf.documentInformation.creator = ""
        pdf.documentInformation.author = "Ministère de l'intérieur"
    }

    private fun printQRCode(
        pdf: PDDocument
    ) {
        val code = GenQRCode(settings.qrData, pdf)
        val page1 = pdf.pages.first()

        PDPageContentStream(pdf, page1, true, true, true).use {
            it.drawImage(code, page1.bBox.width - 156, 100f, 92f, 92f)
            it.saveGraphicsState()
        }

        pdf.addPage(PDPage(page1.mediaBox))

        val page2 = pdf.pages.last()
        PDPageContentStream(pdf, page2, true, true, true).use {
            it.drawImage(code, 50f, page2.bBox.height - 350, 300f, 300f)
            it.saveGraphicsState()
        }
    }

    private fun GenQRCode(payload: String, pdf: PDDocument): PDImageXObject? {
        Log.d("Attestation", "encoding $payload")
        val qrgEncoder = QRGEncoder(payload, null, QRGContents.Type.TEXT, 300)
        val bitmap = qrgEncoder.bitmap!!
        return LosslessFactory.createFromImage(pdf, bitmap)
    }

    internal fun getIdealFontSize(
        text: String,
        maxWidth: Float,
        minSize: Float,
        defaultSize: Float,
        font: PDFont = PDType1Font.HELVETICA
    ): Float? {
        var currentSize = defaultSize
        var textWidth = font.getStringWidth(text) / 1000 * currentSize

        while (textWidth > maxWidth && currentSize > minSize) {
            textWidth = font.getStringWidth(text) / 1000 * currentSize
            currentSize -= 1
        }

        return if (textWidth > maxWidth) null else currentSize
    }


}