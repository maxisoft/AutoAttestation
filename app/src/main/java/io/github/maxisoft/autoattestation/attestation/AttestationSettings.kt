package io.github.maxisoft.autoattestation.attestation

import io.github.maxisoft.autoattestation.attestation.form.Reason
import java.util.*

data class AttestationSettings(
    val creationDate: Date,
    val lastName: String,
    val firstName: String,
    val birthDay: Date,
    val lieuNaissance: String,
    val address: String,
    val zipCode: String,
    val city: String,
    val dateSortie: Date,
    val reasons: List<Reason>
) {

    val formattedBirthDay: String
        get() {
            return dateFormat.format(birthDay)
        }

    val formattedDateSortie: String
        get() {
            return dateFormat.format(dateSortie)
        }

    val formattedHeureSortie: String
        get() {
            return timeFormat.format(dateSortie)
        }

    val formattedReasons: String
        get() {
            return reasons.joinToString(", ") { it.code }
        }

    val qrData: String
        get() {
            val lineSep = ";\n "
            return StringBuilder().apply {
                append("Cree le: ")
                append(dateFormat.format(creationDate))
                append(" a ")
                append(timeFormat.format(creationDate))
                append(lineSep)

                append("Nom: ")
                append(lastName)
                append(lineSep)

                append("Prenom: ")
                append(firstName)
                append(lineSep)

                append("Naissance: ")
                append(formattedBirthDay)
                append(" a ")
                append(lieuNaissance)
                append(lineSep)

                append("Adresse: ")
                append(address)
                append(" ")
                append(zipCode)
                append(" ")
                append(city)
                append(lineSep)

                append("Sortie: ")
                append(formattedDateSortie)
                append(" a ")
                append(formattedHeureSortie)
                append(lineSep)

                append("Motifs: ")
                append(formattedReasons)

            }.toString()
        }


}