package com.otakeeesen.byebyemoneylist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.otakeeesen.byebyemoneylist.ui.theme.ByeByeMoneyListTheme
import com.otakeeesen.byebyemoneylist.ui.components.main.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ByeByeMoneyListTheme {
                MainScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ByeByeMoneyListTheme {
        MainScreen()
    }
}