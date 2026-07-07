package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppDatabase
import com.example.data.repository.WifiRepository
import com.example.ui.screens.ConnectedDetailsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SavedNetworksScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.WifiViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.savedNetworkDao()
        val repository = WifiRepository(applicationContext, dao)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val viewModel: WifiViewModel = viewModel(
                    factory = WifiViewModel.Factory(repository)
                )

                val context = androidx.compose.ui.platform.LocalContext.current
                val permissionsGranted = remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    permissionsGranted.value = result.values.all { it }
                }

                LaunchedEffect(Unit) {
                    val requiredPermissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    
                    val missing = requiredPermissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(navController = navController, startDestination = "home") {
                            composable("home") {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToSaved = { navController.navigate("saved") },
                                    onNavigateToDetails = { navController.navigate("details") }
                                )
                            }
                            composable("saved") {
                                SavedNetworksScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.navigateUp() }
                                )
                            }
                            composable("details") {
                                ConnectedDetailsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.navigateUp() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
