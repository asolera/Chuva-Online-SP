package com.example

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GestureTransformTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `test tap offset inside graphicsLayer`() {
        var recordedTap: Offset? = null

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        scaleX = 2f
                        scaleY = 2f
                        translationX = 50f
                        translationY = 50f
                        transformOrigin = TransformOrigin.Center
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                recordedTap = offset
                            }
                        }
                )
            }
        }

        // Tap on screen center of the 200dp box (which is at 100dp, 100dp)
        composeTestRule.onRoot().performTouchInput {
            // Touch at root (100, 100) in density pixels
            down(Offset(100f, 100f))
            up()
        }

        println("RECORDED TAP: $recordedTap")
    }
}
