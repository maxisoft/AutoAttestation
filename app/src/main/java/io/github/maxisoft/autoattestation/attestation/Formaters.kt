package io.github.maxisoft.autoattestation.attestation

import java.text.SimpleDateFormat
import java.util.*

val dateFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
}

val timeFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("hh:mm aa", Locale.CANADA)
}


val localTimeFormat: SimpleDateFormat by lazy {
    SimpleDateFormat("HH:mm", Locale.FRANCE)
}

val datetimeFileFormat : SimpleDateFormat by lazy {
    SimpleDateFormat("yyyy_MM_dd.HH_mm", Locale.FRANCE)
}