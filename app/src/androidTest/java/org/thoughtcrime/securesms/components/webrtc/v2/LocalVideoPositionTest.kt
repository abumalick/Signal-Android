/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.keyvalue.SignalStore

@RunWith(AndroidJUnit4::class)
class LocalVideoPositionTest {
  @get:Rule
  val composeRule = createComposeRule()

  private var originalCorner: String? = null

  @Before
  fun setUp() {
    originalCorner = SignalStore.misc.localVideoCorner
    SignalStore.misc.localVideoCorner = null
  }

  @After
  fun tearDown() {
    SignalStore.misc.localVideoCorner = originalCorner
    SignalStore.blockUntilAllWritesFinished()
  }

  @Test
  fun cornersSurviveNewCallsAndStorageReload() {
    val call = mutableStateOf(0)
    lateinit var state: PictureInPictureState
    composeRule.setContent {
      key(call.value) {
        state = rememberLocalVideoState(DpSize(60.dp, 90.dp))
      }
    }
    composeRule.runOnIdle {
      assertEquals(PictureInPictureState.Corner.BOTTOM_RIGHT, state.corner)
    }

    for (corner in PictureInPictureState.Corner.entries) {
      composeRule.runOnIdle { state.corner = corner }
      composeRule.waitForIdle()
      SignalStore.blockUntilAllWritesFinished()
      SignalStore.resetCache()
      assertEquals(corner.name, SignalStore.misc.localVideoCorner)
      composeRule.runOnIdle { call.value++ }
      composeRule.runOnIdle { assertEquals(corner, state.corner) }
    }
  }

  @Test
  fun unknownStoredCornerFallsBackToBottomRight() {
    SignalStore.misc.localVideoCorner = "unknown"
    lateinit var state: PictureInPictureState
    composeRule.setContent {
      state = rememberLocalVideoState(DpSize(60.dp, 90.dp))
    }
    composeRule.runOnIdle {
      assertEquals(PictureInPictureState.Corner.BOTTOM_RIGHT, state.corner)
    }
  }

  @Test
  fun draggingPersistsCornerAcrossResizeFocusAndNewCall() {
    val call = mutableStateOf(0)
    val size = mutableStateOf(DpSize(300.dp, 400.dp))
    val focused = mutableStateOf(false)
    lateinit var state: PictureInPictureState
    composeRule.setContent {
      key(call.value) {
        state = rememberLocalVideoState(DpSize(60.dp, 90.dp))
        PictureInPicture(
          state = state,
          isFocused = focused.value,
          modifier = Modifier.size(size.value).testTag("container")
        ) {
          Box(Modifier.fillMaxSize().testTag("self-view"))
        }
      }
    }

    val container = composeRule.onNodeWithTag("container").fetchSemanticsNode().boundsInRoot
    val pip = composeRule.onNodeWithTag("self-view").fetchSemanticsNode().boundsInRoot
    composeRule.onNodeWithTag("self-view").performTouchInput {
      swipe(center, Offset(container.left - pip.left + width / 2f, container.top - pip.top + height / 2f), 1000)
    }
    composeRule.runOnIdle {
      assertEquals(PictureInPictureState.Corner.TOP_LEFT, state.corner)
      assertEquals("TOP_LEFT", SignalStore.misc.localVideoCorner)
      size.value = DpSize(400.dp, 300.dp)
      focused.value = true
    }
    composeRule.runOnIdle {
      assertEquals("TOP_LEFT", SignalStore.misc.localVideoCorner)
      focused.value = false
      call.value++
    }
    composeRule.runOnIdle {
      assertEquals(PictureInPictureState.Corner.TOP_LEFT, state.corner)
    }
    val restored = composeRule.onNodeWithTag("self-view").fetchSemanticsNode().boundsInRoot
    assertEquals(container.left, restored.left, 1f)
    assertEquals(container.top, restored.top, 1f)
  }
}
