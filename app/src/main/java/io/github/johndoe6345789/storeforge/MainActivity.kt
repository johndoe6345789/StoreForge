package io.github.johndoe6345789.storeforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.johndoe6345789.storeforge.ui.StoreApp
import io.github.johndoe6345789.storeforge.ui.StoreForgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StoreViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StoreForgeTheme {
                StoreApp(viewModel)
            }
        }
    }
}
