package com.salroumi.smscleaner.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.salroumi.smscleaner.ui.AppTheme

/** Minimal composer so other apps' "send SMS" links keep working while this app is default. */
class ComposeSmsActivity : ComponentActivity() {

    private var pending: Pair<String, String>? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val p = pending
        if (granted && p != null) doSend(p.first, p.second)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTo = SmsSender.recipientFrom(intent.data)
        val initialBody = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    var to by remember { mutableStateOf(initialTo) }
                    var body by remember { mutableStateOf(initialBody) }
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("رسالة جديدة")
                        OutlinedTextField(to, { to = it }, Modifier.fillMaxWidth(), label = { Text("إلى") })
                        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text("النص") }, minLines = 4)
                        Button(
                            onClick = { send(to, body) },
                            enabled = to.isNotBlank() && body.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("إرسال") }
                    }
                }
            }
        }
    }

    private fun send(to: String, body: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            doSend(to, body)
        } else {
            pending = to to body
            permission.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun doSend(to: String, body: String) {
        try {
            to.split(';', ',').filter { it.isNotBlank() }.forEach { SmsSender.send(this, it.trim(), body) }
            Toast.makeText(this, "تم الإرسال", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر الإرسال: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
