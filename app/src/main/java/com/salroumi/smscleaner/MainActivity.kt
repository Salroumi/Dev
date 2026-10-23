package com.salroumi.smscleaner

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.salroumi.smscleaner.sms.DefaultSmsHelper
import com.salroumi.smscleaner.ui.AppTheme
import com.salroumi.smscleaner.ui.ReviewActions
import com.salroumi.smscleaner.ui.ReviewScreen
import com.salroumi.smscleaner.ui.ReviewViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ReviewViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refreshStatus(hasSmsPermission())
            if (hasSmsPermission()) viewModel.scan()
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onRoleResult(hasSmsPermission())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actions = ReviewActions(
            onGrantPermission = ::requestAppPermissions,
            onScan = viewModel::scan,
            onToggle = viewModel::toggle,
            onFilter = viewModel::setFilter,
            onQuery = viewModel::setQuery,
            onTextQuery = viewModel::setTextQuery,
            onSelectAllVisible = viewModel::selectAllVisible,
            onRequestDefault = { launchSafely(DefaultSmsHelper.requestDefaultIntent(this)) },
            onDelete = viewModel::deleteSelected,
            onRestoreDefault = {
                viewModel.dismissRestoreHint()
                launchSafely(DefaultSmsHelper.restoreIntent(this))
            },
            onDismissRestore = viewModel::dismissRestoreHint,
            onOpenAppSettings = {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )
            },
            onMessageShown = viewModel::messageShown,
        )

        setContent {
            AppTheme {
                val state by viewModel.state.collectAsState()
                ReviewScreen(state, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The default SMS app may have changed in system settings while we were away.
        viewModel.refreshStatus(hasSmsPermission())
    }

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun requestAppPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun launchSafely(intent: Intent) {
        try {
            roleLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
