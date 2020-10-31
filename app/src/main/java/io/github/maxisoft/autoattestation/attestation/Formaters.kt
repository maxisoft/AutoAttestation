package io.github.maxisoft.autoattestation.attestation

import java.text.SimpleDateFormat
import java.util.*

val dateFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
}

val timeFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("HH:mm", Locale.FRANCE)
}