package io.github.maxisoft.autoattestation.attestation.form

import com.squareup.moshi.Json

data class Reason(
    val id: String,
    val code: String,
    @Json(name = "pdf_offset_y") val pdfOffsetY: Float
)