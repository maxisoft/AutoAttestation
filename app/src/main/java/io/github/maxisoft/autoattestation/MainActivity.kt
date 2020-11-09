package io.github.maxisoft.autoattestation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.SparseIntArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.util.containsKey
import androidx.core.util.set
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import com.takisoft.preferencex.DatePickerPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.TimePickerPreference
import io.github.maxisoft.autoattestation.android.AsyncTaskEzAny
import io.github.maxisoft.autoattestation.android.showAsActionFlag
import io.github.maxisoft.autoattestation.attestation.AttestationRepository
import io.github.maxisoft.autoattestation.attestation.AttestationSettings
import io.github.maxisoft.autoattestation.attestation.dateFormat
import io.github.maxisoft.autoattestation.attestation.form.FormDataAdapterService
import io.github.maxisoft.autoattestation.attestation.localTimeFormat
import io.github.maxisoft.autoattestation.attestation.task.GeneratePdfTask
import io.github.maxisoft.autoattestation.attestation.task.QuickSavePdfTask
import io.github.maxisoft.autoattestation.attestation.task.SaveAsPdfTask
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.absoluteValue


class MainActivity : AppCompatActivity() {

    private var pendingPdf: GeneratePdfTask? = null
    private lateinit var settingsFragment: SettingsFragment
    private lateinit var formDataAdapterService: FormDataAdapterService
    internal val attestationRepository: AttestationRepository by lazy {
        AttestationRepository(this)
    }

    override fun onStart() {
        resources.openRawResource(R.raw.form_data_adapter).use {
            formDataAdapterService = FormDataAdapterService.createFromStream(it)
        }
        super.onStart()
    }

    val content: View
        get() = findViewById<View>(android.R.id.content)

    val rootView: View
        get() = content.rootView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null || supportFragmentManager.findFragmentByTag(SettingsFragment::class.simpleName) !is SettingsFragment) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.settings,
                    SettingsFragment().also { settingsFragment = it },
                    SettingsFragment::class.simpleName
                )
                .commit()
        } else {
            settingsFragment =
                supportFragmentManager.findFragmentByTag(SettingsFragment::class.simpleName) as SettingsFragment
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.mainmenu, menu)
        if (menu != null) {
            doUpdateActions(menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    private val actionVisibilityMap: SparseIntArray = SparseIntArray(8)

    private fun doUpdateActions(menu: Menu) {

        fun getOriginalVisibility(id: Int): Int {
            return if (actionVisibilityMap.containsKey(id)) {
                actionVisibilityMap[id]
            } else {
                menu.findItem(id).showAsActionFlag.also {
                    actionVisibilityMap[id] = it
                }
            }
        }

        menu.findItem(R.id.action_open_prev).apply {
            val original = getOriginalVisibility(itemId)
            val visible = attestationRepository.hasLatestPdf
            setShowAsAction(if (visible) MenuItem.SHOW_AS_ACTION_IF_ROOM else original)
        }

        val actionVisibility = settingsFragment.changeCounter > 0

        arrayOf(
            R.id.action_show_qr,
            R.id.action_save,
            R.id.action_save_as,
            R.id.action_create_shortcut
        ).forEach {
            val original = getOriginalVisibility(it)
            menu.findItem(it).apply {
                setShowAsAction(if (actionVisibility) original else MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        doUpdateActions(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun ShortcutIcon() {
        val shortcutIntent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val addIntent = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, "Test")
            putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(
                    applicationContext, R.drawable.ic_launcher_foreground
                )
            )
            putExtra("duplicate", false)
            action = "com.android.launcher.action.INSTALL_SHORTCUT"
        }
        applicationContext.sendBroadcast(addIntent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        fun checkFirstUse(continuation: () -> Unit = {}) {
            if (settingsFragment.changeCounter > 0) {
                continuation()
            } else {
                Snackbar.make(content, getString(R.string.please_complete_form), Snackbar.LENGTH_SHORT)
                    .apply {
                        setAction(getString(R.string.ignore).capitalize(Locale.getDefault())) { continuation() }
                        show()
                    }
            }
        }

        when (item.itemId) {
            R.id.action_save -> {
                checkFirstUse { startPdfGeneration { QuickSavePdfTask(it, WeakReference(this)) } }
            }
            R.id.action_save_as -> {
                checkFirstUse { startPdfGeneration { SaveAsPdfTask(it, WeakReference(this)) } }
            }
            R.id.action_show_qr -> {
                checkFirstUse {
                    startActivity(AttestationSummary.create(this, getAttestationSettings()))
                }
            }
            R.id.action_open_prev -> {
                if (attestationRepository.hasLatestPdf) {
                    openPdf(attestationRepository.latestPdfFile.toUri())
                } else {
                    Snackbar.make(content, getString(R.string.no_pdf_found), Snackbar.LENGTH_SHORT).apply {
                        setAction(getString(R.string.generate)) {
                            startPdfGeneration {
                                QuickSavePdfTask(it, WeakReference(this@MainActivity))
                            }
                            dismiss()
                        }
                        show()
                    }
                }
            }
            R.id.action_create_shortcut -> {
                ShortcutIcon()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun <T : GeneratePdfTask> startPdfGeneration(factory: (AttestationSettings) -> T) {
        pendingPdf?.let {
            if (it.status != AsyncTask.Status.FINISHED) {
                Log.d("Attestation", "cancelling previous pdf gen task")
                it.cancel(true)
            }
        }
        val attestationSettings = getAttestationSettings()
        Log.d("Attestation", "using settings: $attestationSettings")
        pendingPdf = factory(attestationSettings).apply {
            execute(
                attestationSettings
            )
        }
    }

    private fun getAttestationSettings(): AttestationSettings {
        fun getTextPref(key: String, default: String = ""): String {
            val pref =
                settingsFragment.preferenceManager.findPreference<DialogPreference>(key)
                    ?: error("unable to find pref with key $key")

            val ret = when (pref) {
                is EditTextPreference -> {
                    pref.text?.trim()
                }
                is DatePickerPreference -> {
                    dateFormat.format(pref.date!!)
                }
                is TimePickerPreference -> {
                    localTimeFormat.format(pref.time!!)
                }
                else -> {
                    ""
                }
            }

            return if (ret.isNullOrBlank()) default else ret
        }

        val birthday = dateFormat.parse(getTextPref("birthday"))
            ?: error("unable to parse birth date")
        val dateSortie =
            dateFormat.parse(getTextPref("date")) ?: error("unable to parse date")
        val heureSortie =
            localTimeFormat.parse(getTextPref("hour")) ?: error("unable to parse time part")
        val calendar = Calendar.getInstance().apply {
            time = dateSortie
            Calendar.getInstance().also {
                it.time = heureSortie
                arrayOf(
                    Calendar.HOUR_OF_DAY,
                    Calendar.MINUTE,
                    Calendar.SECOND,
                    Calendar.MILLISECOND
                ).forEach { field ->
                    set(field, it.get(field))
                }
            }
        }

        Log.i("Attestation", "parsed calendar: $calendar")

        val reasons = formDataAdapterService.listReasons().filter { (key, _) ->
            val pref =
                settingsFragment.preferenceManager.findPreference<SwitchPreferenceCompat>(
                    key
                )
                    ?: throw Resources.NotFoundException("unable to find pref with key $key")
            pref.isChecked
        }.map { (_, reason) -> reason }


        val maxCreationDate = (calendar.clone() as Calendar).apply {
            val rnd = ((Random(calendar.hashCode().toLong())
                .nextGaussian() * 15).absoluteValue + 2).coerceIn(1.0, 120.0)
            add(Calendar.SECOND, (rnd * -60).toInt())
        }

        return AttestationSettings(
            creationDate = Date().coerceAtMost(maxCreationDate.time),
            firstName = getTextPref("firstname"),
            lastName = getTextPref("lastname"),
            birthDay = birthday,
            lieuNaissance = getTextPref("birthplace"),
            address = getTextPref("address"),
            zipCode = getTextPref("zipcode"),
            city = getTextPref("city"),
            dateSortie = calendar.time,
            reasons = reasons.toList()
        )
    }

    private val bufferQueue: ArrayDeque<Pair<ByteArray, AttestationSettings>> = ArrayDeque()

    internal fun createFile(result: ByteArray, settings: AttestationSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            synchronized(bufferQueue) {
                bufferQueue.addLast(Pair(result, settings))
                try {
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_TITLE, "attestation.pdf")
                    }, CREATE_FILE)
                } catch (e: Exception) {
                    bufferQueue.removeLast()
                    throw e
                }
            }

        } else {
            val target =
                if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                } else {
                    getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.absolutePath
                }

            val path = File(File(target), "/attestation.pdf")

            AsyncTaskEzAny {
                FileOutputStream(path).use { stream ->
                    attestationRepository.saveInto(result, settings, stream)
                }
            }.execute {
                createOpenPdfSnackbar()
            }
        }
    }

    internal fun createOpenPdfSnackbar(show: Boolean = true): Snackbar {
        require(attestationRepository.hasLatestPdf)
        return createOpenPdfSnackbar(attestationRepository.latestPdfFile.toUri(), show)
    }

    private fun createOpenPdfSnackbar(uri: Uri, show: Boolean = true): Snackbar {
        invalidateOptionsMenu()
        return Snackbar.make(content, getString(R.string.pdf_created), Snackbar.LENGTH_LONG).also {
            it.setAction(getString(R.string.open)) { runOnUiThread { openPdf(uri) } }
            if (show) it.show()
        }
    }

    private fun openPdf(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_VIEW)
        var effectiveUri = uri
        if (uri.scheme == "file") {
            effectiveUri = FileProvider.getUriForFile(
                this,
                "${application.packageName}.provider",
                uri.toFile()
            )
        }
        shareIntent.setDataAndType(effectiveUri, "application/pdf")
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (shareIntent.resolveActivity(packageManager) != null) {
            startActivity(shareIntent)
        } else {
            openWithPdf(uri)
        }
    }

    private fun openWithPdf(uri: Uri) {
        val target = Intent(Intent.ACTION_VIEW)
        var effectiveUri = uri
        if (uri.scheme == "file") {
            effectiveUri = FileProvider.getUriForFile(
                this,
                "${application.packageName}.provider",
                uri.toFile()
            )
        }
        target.setDataAndType(effectiveUri, "application/pdf")
        target.flags =
            target.flags or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION

        val intent = Intent.createChooser(target, "Open File")
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("Attestation", "unable to find a pdf reader", e)
            Toast.makeText(this, getString(R.string.no_pdf_reader_app), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        pendingPdf?.cancel(true)
        pendingPdf = null
        synchronized(bufferQueue) {
            bufferQueue.clear()
        }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == CREATE_FILE) {
            val (buff, settings) = synchronized(bufferQueue) {
                bufferQueue.removeFirst()
            }
            if (resultCode == Activity.RESULT_OK) {
                resultData?.data?.also { uri ->
                    AsyncTaskEzAny {
                        contentResolver.openOutputStream(uri)?.use { stream ->
                            attestationRepository.saveInto(buff, settings, stream)
                        }
                    }.execute {
                        createOpenPdfSnackbar()
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, resultData)
        }
    }


    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            updateDateTimeFields()
            attachDateTimeValidators()
            attachChangeCounter()
            if (changeCounter > 0) {
                scrollToPreference("date")
            }
        }

        internal val changeCounter: Long
            get() = preferenceManager.sharedPreferences.getLong("change_counter", 0)

        private fun attachChangeCounter() {
            arrayOf(
                "address",
                "city",
                "zipcode",
                "birthday",
                "birthplace",
                "firstname",
                "lastname"
            ).forEach {
                findPreference<DialogPreference>(it)!!.let { pref ->
                    val prev = pref.onPreferenceChangeListener
                    pref.onPreferenceChangeListener =
                        Preference.OnPreferenceChangeListener { preference, newValue ->
                            preferenceManager.sharedPreferences.apply {
                                val counter = getLong("change_counter", 0)
                                edit().putLong(
                                    "change_counter",
                                    counter + 1
                                ).apply()

                                if (counter <= 0) {
                                    activity?.invalidateOptionsMenu()
                                }
                            }
                            prev?.onPreferenceChange(preference, newValue) ?: true
                        }
                }
            }
        }

        private fun updateDateTimeFields() {
            val calendar = Calendar.getInstance().also {
                val rnd = (Random().nextGaussian() * 5 + 3).coerceIn(1.0, 11.0)
                it.add(Calendar.SECOND, (rnd * -60).toInt())
            }

            findPreference<DatePickerPreference>("date")!!.apply {
                date = calendar.time
                pickerDate = date
            }
            findPreference<TimePickerPreference>("hour")!!.apply {
                setDefaultValue(localTimeFormat.format(calendar.time))
                time = calendar.time
                pickerTime = time
            }
        }

        private fun attachDateTimeValidators() {
            arrayOf("date", "birthday").forEach { key ->
                findPreference<DatePickerPreference>(key)!!.apply {
                    maxDate = Calendar.getInstance().apply {
                        add(Calendar.YEAR, 1)
                    }.time
                }
            }
        }

    }
}