package io.github.maxisoft.autoattestation.attestation.task

import android.os.AsyncTask
import com.google.android.material.snackbar.Snackbar
import io.github.maxisoft.autoattestation.MainActivity
import io.github.maxisoft.autoattestation.R
import io.github.maxisoft.autoattestation.attestation.AttestationGenerator
import io.github.maxisoft.autoattestation.attestation.AttestationSettings
import java.lang.ref.WeakReference

internal abstract class GeneratePdfTask(
    val settings: AttestationSettings,
    private val weakcontext: WeakReference<MainActivity>
) :
    AsyncTask<AttestationSettings, Void, ByteArray>() {

    protected var snackbar: Snackbar? = null

    val context: MainActivity
        get() = weakcontext.get() ?: error("activity is gone")

    override fun onPreExecute() {
        snackbar =
            Snackbar.make(context.content, context.getString(R.string.generating_pdf), Snackbar.LENGTH_INDEFINITE)
                .also {
                    it.setAction(context.getString(R.string.cancel)) { this.cancel(true) }
                    it.show()
                }
    }

    override fun doInBackground(vararg params: AttestationSettings?): ByteArray {
        return try {
            context.resources.openRawResource(R.raw.certificate).use {
                AttestationGenerator(settings).generate(it)
            }
        } catch (e: InterruptedException) {
            ByteArray(0)
        } finally {
            closeDialogs()
        }
    }

    protected fun closeDialogs() {
        snackbar?.let {
            if (it.isShown) it.dismiss()
        }
    }

    abstract override fun onPostExecute(result: ByteArray?)

    override fun onCancelled() {
        closeDialogs()
    }
}