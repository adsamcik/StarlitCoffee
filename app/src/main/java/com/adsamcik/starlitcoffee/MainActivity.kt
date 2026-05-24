package com.adsamcik.starlitcoffee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.adsamcik.starlitcoffee.navigation.StarlitNavHost
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StarlitCoffeeTheme {
                StarlitNavHost()
            }
        }
    }
}
