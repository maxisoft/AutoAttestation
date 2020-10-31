package io.github.maxisoft.autoattestation

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import io.github.maxisoft.autoattestation.attestation.AttestationGenerator
import io.github.maxisoft.autoattestation.attestation.AttestationSettings
import io.github.maxisoft.autoattestation.attestation.dateFormat
import io.github.maxisoft.autoattestation.attestation.form.FormDataAdapterService
import io.github.maxisoft.autoattestation.attestation.timeFormat
import java.io.File
import java.io.FileOutputStream
import java.util.*


class MainActivity : AppCompatActivity() {

    private var pendingPdf: GeneratePdfTask? = null
    private lateinit var settingsFragment: SettingsFragment
    private lateinit var formDataAdapterService: FormDataAdapterService

    override fun onStart() {
        resources.openRawResource(R.raw.form_data_adapter).use {
            formDataAdapterService = FormDataAdapterService.createFromStream(it)
        }
        super.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment().also { settingsFragment = it })
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.mainmenu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                fun getTextPref(key: String, default: String = ""): String {
                    val pref =
                        settingsFragment.preferenceManager.findPreference<EditTextPreference>(key)
                            ?: error("unable to find pref with key $key")
                    val ret = pref.text?.trim()
                    return if (ret.isNullOrBlank()) default else ret
                }

                val birthday = dateFormat.parse(getTextPref("birthdate"))
                    ?: error("unable to parse birth date")
                val dateSortie =
                    dateFormat.parse(getTextPref("date")) ?: error("unable to parse date")
                val heureSortie =
                    timeFormat.parse(getTextPref("hour")) ?: error("unable to parse time part")
                val calendar = Calendar.getInstance().apply {
                    time = dateSortie
                    Calendar.getInstance().also {
                        it.time = heureSortie
                        arrayOf(Calendar.HOUR, Calendar.MINUTE, Calendar.SECOND).forEach { field ->
                            set(field, it.get(field))
                        }
                    }
                }

                val reasons = formDataAdapterService.listReasons().filter { (key, _) ->
                    val pref =
                        settingsFragment.preferenceManager.findPreference<SwitchPreferenceCompat>(
                            key
                        )
                            ?: throw Resources.NotFoundException("unable to find pref with key $key")
                    pref.isChecked
                }.map { (_, reason) -> reason }

                val attestationSettings = AttestationSettings(
                    creationDate = Date().coerceAtMost(calendar.time),
                    firstName = getTextPref("firstname"),
                    lastName = getTextPref("lastname"),
                    birthDay = birthday,
                    lieuNaissance = getTextPref("birthplace"),
                    address = getTextPref("address"),
                    zipCode = getTextPref("zipcode"),
                    city = getTextPref("town"),
                    dateSortie = calendar.time,
                    reasons = reasons.toList()
                )
                Log.d("Attestation", "using settings: $attestationSettings")

                pendingPdf = GeneratePdfTask(this).apply { execute(attestationSettings) }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private class GeneratePdfTask(private val context: MainActivity) :
        AsyncTask<AttestationSettings, Void, ByteArray>() {
        override fun doInBackground(vararg params: AttestationSettings?): ByteArray {
            return try {
                context.resources.openRawResource(R.raw.certificate).use {
                    AttestationGenerator(params[0]!!, context.formDataAdapterService).generate(it)
                }
            } catch (e: InterruptedException) {
                ByteArray(0)
            }
        }

        override fun onPostExecute(result: ByteArray?) {
            context.createFile(result)
            super.onPostExecute(result)
        }
    }

    private fun createFile(result: ByteArray?) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, "attestation.pdf")
            }
            startActivityForResult(intent, CREATE_FILE)
        } else {
            try {
                val target =
                    if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                    } else {
                        getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.absolutePath
                    }

                FileOutputStream(File(File(target), "/attestation.pdf")).use { stream ->
                    stream.write(pendingPdf?.get()!!)
                }
            } finally {
                pendingPdf = null
            }
        }

    }

    override fun onDestroy() {
        pendingPdf?.cancel(true)
        pendingPdf = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == CREATE_FILE
            && resultCode == Activity.RESULT_OK
        ) {
            try {
                resultData?.data?.also { uri ->
                    contentResolver.openFileDescriptor(uri, "w")?.use {
                        FileOutputStream(it.fileDescriptor).use { stream ->
                            stream.write(pendingPdf?.get()!!)
                        }
                    }
                }
            } finally {
                pendingPdf = null
            }

        } else {
            super.onActivityResult(requestCode, resultCode, resultData)
        }


    }


    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            updateDateTimeFields()
            attachDateTimeValidators()
        }

        private fun updateDateTimeFields() {
            val calendar = Calendar.getInstance().also {
                val rnd = (Random().nextGaussian() * 5 + 3).coerceIn(1.0, 11.0)
                it.add(Calendar.SECOND, (rnd * -60).toInt())
            }

            findPreference<EditTextPreference>("date")?.apply {
                val today = dateFormat.format(calendar.time)
                setDefaultValue(today)
                if ((text ?: "") != today) text = today
            }
            findPreference<EditTextPreference>("hour")?.apply {
                val time = timeFormat.format(calendar.time)
                setDefaultValue(time)
                if ((text ?: "") != time) text = time
            }
        }

        private fun attachDateTimeValidators() {
            arrayOf("date", "birthdate").forEach { key ->
                findPreference<EditTextPreference>(key)?.apply {
                    onPreferenceChangeListener =
                        Preference.OnPreferenceChangeListener { _, value ->
                            try {
                                dateFormat.parse(value.toString())
                                true
                            } catch (e: Exception) {
                                // TODO warn
                                false
                            }
                        }
                }
            }

            arrayOf("hour").forEach { key ->
                findPreference<EditTextPreference>(key)?.apply {
                    onPreferenceChangeListener =
                        Preference.OnPreferenceChangeListener { _, value ->
                            try {
                                timeFormat.parse(value.toString())
                                true
                            } catch (e: Exception) {
                                // TODO warn
                                false
                            }
                        }
                }
            }
        }

    }
}