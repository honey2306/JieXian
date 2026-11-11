package com.jiexian.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiexian.app.enforce.ShizukuEnforcement
import com.jiexian.app.enforce.ShizukuShell
import com.jiexian.app.model.AppRule
import com.jiexian.app.ui.MainScreen
import com.jiexian.app.ui.MainViewModel
import com.jiexian.app.ui.RuleEditorScreen
import com.jiexian.app.ui.theme.JieXianTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST) {
                mainViewModel.refresh()
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    mainViewModel.onSystemLockGranted()
                }
            }
        }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { mainViewModel.refresh() }
    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            ShizukuShell.reset()
            mainViewModel.refresh()
        }
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { mainViewModel.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        }
        setContent {
            JieXianTheme {
                val state by mainViewModel.state.collectAsStateWithLifecycle()
                var showEditor by remember { mutableStateOf(false) }
                var editingRule by remember { mutableStateOf<AppRule?>(null) }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    if (
                        Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                LaunchedEffect(state.message) {
                    state.message?.let {
                        snackbarHostState.showSnackbar(it)
                        mainViewModel.consumeMessage()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    // Each screen handles its own status/navigation bar insets.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        if (showEditor) {
                            RuleEditorScreen(
                                existingRule = editingRule,
                                state = state,
                                onLoadApps = mainViewModel::loadApps,
                                onBack = { showEditor = false },
                                onSave = { rule ->
                                    mainViewModel.saveRule(rule, editingRule != null)
                                    showEditor = false
                                },
                                onDelete = { rule ->
                                    mainViewModel.deleteRule(rule)
                                    showEditor = false
                                },
                            )
                        } else {
                            MainScreen(
                                state = state,
                                onRequestSystemLock = {
                                    val state = mainViewModel.state.value.systemLock
                                    if (state == ShizukuEnforcement.State.READY) {
                                        mainViewModel.refresh()
                                    } else {
                                        ShizukuEnforcement.requestPermission(SHIZUKU_PERMISSION_REQUEST)
                                    }
                                },
                                onCheckUpdates = mainViewModel::checkForUpdates,
                                onInstallUpdate = mainViewModel::installUpdate,
                                onAddRule = {
                                    editingRule = null
                                    showEditor = true
                                },
                                onEditRule = {
                                    editingRule = it
                                    showEditor = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refresh()
    }

    override fun onDestroy() {
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
        super.onDestroy()
    }

    private companion object {
        const val SHIZUKU_PERMISSION_REQUEST = 4102
    }
}
