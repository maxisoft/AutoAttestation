package io.github.maxisoft.autoattestation

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Point
import android.os.AsyncTask
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
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


    private fun resizeTextToFit(textView: TextView) {
        val text = textView.text.toString()

        val minTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            7f,
            resources.displayMetrics
        )

        val measurePaint = Paint(textView.paint)
        var width: Float = measurePaint.measureText(text)
        val labelWidth: Float = textView.width.toFloat()
        val maxLines: Int = textView.maxLines

        while (labelWidth > 0 && width / maxLines > labelWidth - 20) {
            val textSize: Float = measurePaint.textSize
            measurePaint.textSize = textSize - 1
            width = measurePaint.measureText(text)
            if (textSize < minTextSize) break
        }

        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, measurePaint.textSize)
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