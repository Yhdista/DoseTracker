package com.yhdista.dosetracker.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yhdista.dosetracker.core.Data

/**
 * Standard scaffold body for a [Data]-bearing screen: centered spinner while Loading,
 * centered message on Error, [content] on Success. Pass the Scaffold's content padding
 * in [modifier] so the Loading/Error states respect it too — several screens used to
 * forget it and drew under the top app bar.
 */
@Composable
fun <T> DataContent(
    data: Data<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (data) {
        Data.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is Data.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: ${data.message}")
        }
        is Data.Success -> content(data.data)
    }
}
