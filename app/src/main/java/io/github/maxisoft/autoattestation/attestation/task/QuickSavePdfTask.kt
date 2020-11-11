package io.github.maxisoft.autoattestation.attestation.task

import io.github.maxisoft.autoattestation.MainActivity
import io.github.maxisoft.autoattestation.attestation.AttestationSettings
import java.lang.ref.WeakReference

internal class QuickSavePdfTask(
    settings: AttestationSettings,
    weakcontext: WeakReference<MainActivity>
) : GeneratePdfTask(settings, weakcontext) {

    override fun onPostExecute(result: ByteArray?) {
        result?.let { buff ->
            context.attestationRepository.save(buff, settings).also {
                context.createOpenPdfSnackbar()
            }
        }

        closeDialogs()
    }
}