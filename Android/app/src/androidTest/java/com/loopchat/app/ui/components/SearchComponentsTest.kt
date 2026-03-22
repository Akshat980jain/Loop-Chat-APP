package com.loopchat.app.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loopchat.app.data.models.Profile
import com.loopchat.app.ui.theme.LoopChatTheme
import org.junit.Rule
import org.junit.Test

class SearchComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchDialog_showsTitleAndPlaceholder() {
        // Arrange & Act
        composeTestRule.setContent {
            LoopChatTheme {
                SearchDialog(
                    searchQuery = "",
                    onQueryChange = {},
                    searchResults = emptyList(),
                    isSearching = false,
                    isAddingContact = false,
                    errorMessage = null,
                    onDismiss = {},
                    onUserSelect = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Search Users").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search by email or username").assertIsDisplayed()
    }

    @Test
    fun searchDialog_showsResults_whenNotEmpty() {
        // Arrange
        val testProfiles = listOf(
            Profile(id = "1", username = "alice", fullName = "Alice Smith"),
            Profile(id = "2", username = "bob", fullName = "Bob Jones")
        )

        // Act
        composeTestRule.setContent {
            LoopChatTheme {
                SearchDialog(
                    searchQuery = "test",
                    onQueryChange = {},
                    searchResults = testProfiles,
                    isSearching = false,
                    isAddingContact = false,
                    errorMessage = null,
                    onDismiss = {},
                    onUserSelect = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Alice Smith").assertIsDisplayed()
        composeTestRule.onNodeWithText("alice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob Jones").assertIsDisplayed()
        composeTestRule.onNodeWithText("bob").assertIsDisplayed()
    }

    @Test
    fun searchDialog_showsLoading_whenSearching() {
        // Arrange & Act
        composeTestRule.setContent {
            LoopChatTheme {
                SearchDialog(
                    searchQuery = "test",
                    onQueryChange = {},
                    searchResults = emptyList(),
                    isSearching = true,
                    isAddingContact = false,
                    errorMessage = null,
                    onDismiss = {},
                    onUserSelect = {}
                )
            }
        }

        // Assert
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    @Test
    fun searchDialog_showsErrorMessage_onFailure() {
        // Arrange
        val errorMsg = "Something went wrong"

        // Act
        composeTestRule.setContent {
            LoopChatTheme {
                SearchDialog(
                    searchQuery = "test",
                    onQueryChange = {},
                    searchResults = emptyList(),
                    isSearching = false,
                    isAddingContact = false,
                    errorMessage = errorMsg,
                    onDismiss = {},
                    onUserSelect = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText(errorMsg).assertIsDisplayed()
    }
}
