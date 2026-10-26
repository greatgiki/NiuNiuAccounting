package com.example.localledger.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CaptureConfirmActivity : ComponentActivity() {
    @Inject lateinit var coordinator: CaptureCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!coordinator.attachActivity(this)) finish()
    }
}
