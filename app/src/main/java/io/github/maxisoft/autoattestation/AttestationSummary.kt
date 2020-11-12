package io.github.maxisoft.autoattestation

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidmads.library.qrgenearator.QRGEncoder
import androidx.appcompat.app.AppCompatActivity
import com.squareup.moshi.JsonAdapter
import io.github.maxisoft.autoattestation.Application.Companion.moshi
import io.github.maxisoft.autoattestation.attestation.AttestationGenerator
import io.github.maxisoft.autoattestation.attestation.AttestationSettings
import java.lang.ref.WeakReference
import kotlin.math.min


class AttestationSummary : AppCompatActivity() {
    private var settings: AttestationSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getString("payload")?.let {
            settings = adapter.fromJson(it)
        }
        setContentView(R.layout.activity_attestation_summary)

        settings?.let {
            findViewById<ImageView>(R.id.qrcode).apply {
                visibility = View.INVISIBLE
                val dim = (getSystemService(WINDOW_SERVICE) as WindowManager).let { wm ->
                    Point().also { pt -> wm.defaultDisplay.getSize(pt) }
                        .let { pt -> min(pt.x, pt.y) }
                }
                GenQrTask(it, WeakReference(this)).execute(dim.coerceAtLeast(150))
            }

            findViewById<TextView>(R.id.qrtranscription).apply {
                text = it.qrData
                resizeTextToFit(this)
            }
        }

        findViewById<View>(R.id.backButton).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                elevation = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    7f,
                    resources.displayMetrics
                )
            }
            setOnClickListener {
                packageManager.getLaunchIntentForPackage("fr.gouv.android.stopcovid")?.let {
                    startActivity(it)
                    return@setOnClickListener
                }
                finish()
            }
            setOnLongClickListener {
                finish()
                true
            }
        }
    }


    private fun resizeTextToFit(textView: TextView, counter: Long = 0) {
        val minTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            3f,
            resources.displayMetrics
        )

        if (!textView.getGlobalVisibleRect(Rect())) {
            require(counter < 130L)
            Handler(mainLooper).post { resizeTextToFit(textView, counter + 1) }
            return
        }

        Log.d("Attestation", "iter: ${counter + 1}")
        if (textView.layout.getEllipsisCount(textView.lineCount - 1) > 0) {
            if (counter == 0L) {
                textView.alpha = 0.1f
            }
            val newSize = textView.textSize * 0.9f
            if (newSize > minTextSize) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize)
                Handler(mainLooper).post { resizeTextToFit(textView, counter + 1) }
            } else {
                Log.w(
                    "Attestation",
                    "unable to find font size in ${counter + 1} iteration for text ${textView.text}"
                )
                textView.alpha = 1f
                require(counter != 0L)
            }
        } else {
            textView.alpha = 1f
            Log.d(
                "Attestation",
                "Using text size ${textView.textSize} using ${counter + 1} iterations"
            )
        }

    }

    private class GenQrTask(
        val settings: AttestationSettings,
        val imageView: WeakReference<ImageView>
    ) : AsyncTask<Int, Void, QRGEncoder>() {
        override fun doInBackground(vararg params: Int?): QRGEncoder {
            return AttestationGenerator(settings).genQRCode(params[0]!!)
        }

        override fun onPostExecute(result: QRGEncoder?) {
            result?.let {
                imageView.get()?.apply {
                    setImageBitmap(it.bitmap!!)
                    visibility = View.VISIBLE
                    invalidate()
                }
            }
        }

        override fun onCancelled() {
            imageView.get()?.apply {
                visibility = View.GONE
            }
        }
    }

    companion object {
        fun create(context: Context, attestationSettings: AttestationSettings): Intent {
            return Intent(context, AttestationSummary::class.java).also {
                it.putExtras(Bundle().apply {
                    putString("payload", adapter.toJson(attestationSettings))
                })
            }
        }

        private val adapter: JsonAdapter<AttestationSettings> by lazy {
            moshi.adapter(AttestationSettings::class.java)
        }
    }
}