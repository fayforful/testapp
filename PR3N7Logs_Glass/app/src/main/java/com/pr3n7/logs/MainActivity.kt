package com.pr3n7.logs

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pr3n7.logs.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val calendar = Calendar.getInstance()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { !it }) {
            Toast.makeText(this, getString(R.string.error_permission), Toast.LENGTH_SHORT).show()
        }
    }

    private val pickContact = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val contactId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: ""
                if (cursor.getInt(hasPhoneIdx) > 0) {
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )?.use { phoneCursor ->
                        if (phoneCursor.moveToFirst()) {
                            val phoneIdx = phoneCursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            )
                            val phone = phoneCursor.getString(phoneIdx) ?: ""
                            binding.etPhone.setText(phone)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupListeners()
        updateDateTimeFields()
        checkPermissions()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.call_types,
            android.R.layout.simple_spinner_dropdown_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerType.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnContact.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                pickContact.launch(null)
            } else {
                requestPermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            }
        }

        binding.btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    updateDateTimeFields()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    updateDateTimeFields()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        binding.btnGenerate.setOnClickListener { generateCallLog() }
    }

    private fun updateDateTimeFields() {
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.etDate.text =(dateFmt.format(calendar.time))
        binding.etTime.text =(timeFmt.format(calendar.time))
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.WRITE_CALL_LOG)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.READ_CALL_LOG)
        if (needed.isNotEmpty()) {
            requestPermissions.launch(needed.toTypedArray())
        }
    }

    private fun generateCallLog() {
        val phone = binding.etPhone.text.toString().trim()
        if (phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty), Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, getString(R.string.error_permission), Toast.LENGTH_SHORT).show()
            requestPermissions.launch(
                arrayOf(
                    Manifest.permission.WRITE_CALL_LOG,
                    Manifest.permission.READ_CALL_LOG
                )
            )
            return
        }

        val type = when (binding.spinnerType.selectedItemPosition) {
            0 -> CallLog.Calls.INCOMING_TYPE
            1 -> CallLog.Calls.OUTGOING_TYPE
            2 -> CallLog.Calls.MISSED_TYPE
            3 -> CallLog.Calls.REJECTED_TYPE
            4 -> CallLog.Calls.BLOCKED_TYPE
            else -> CallLog.Calls.INCOMING_TYPE
        }

        val durationStr = binding.etDuration.text.toString().trim()
        val duration = durationStr.toLongOrNull() ?: 0L

        val values = ContentValues().apply {
            put(CallLog.Calls.NUMBER, phone)
            put(CallLog.Calls.TYPE, type)
            put(CallLog.Calls.DATE, calendar.timeInMillis)
            put(CallLog.Calls.DURATION, duration)
            put(CallLog.Calls.NEW, 1)
        }

        try {
            contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
            Toast.makeText(this, getString(R.string.success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
