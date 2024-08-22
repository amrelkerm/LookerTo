package com.example.lookerto

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import com.example.lookerto.databinding.ActivityMainBinding
import android.util.Log
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    private val TAG = "MyAppError In "
    private val TAG2 = "RunFun  In "


    private lateinit var binding: ActivityMainBinding

    // ... (requestPermissionLauncher code)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener {
            // Check for overlay permission (if needed for floating activity)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                // Request overlay permission
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                // Open the FloatingActivity
                val intent = Intent(this, floting::class.java)
                startActivity(intent)

                // Request ignore battery optimization permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestIgnoreBatteryOptimization()
                }
            }
        }
    }

    // ... (onResume and isMyServiceRunning methods - remove if not needed)


    private fun requestIgnoreBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = android.net.Uri.parse("package:$packageName")
        startActivity(intent)
    }
}
