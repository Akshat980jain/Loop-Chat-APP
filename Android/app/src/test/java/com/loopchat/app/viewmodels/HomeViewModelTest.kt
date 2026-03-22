package com.loopchat.app.ui.viewmodels

import com.loopchat.app.data.ConversationWithParticipant
import com.loopchat.app.data.SupabaseClient
import com.loopchat.app.data.SupabaseRepository
import com.loopchat.app.data.models.Profile
import com.loopchat.app.testutil.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        mockkObject(SupabaseClient)
        mockkObject(SupabaseRepository)
        
        // Default mock for user ID
        every { SupabaseClient.currentUserId } returns "test_user_id"
        
        viewModel = HomeViewModel()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `loadConversations updates conversations state on success`() = runTest {
        // Arrange
        val mockConversations = listOf(
            ConversationWithParticipant(
                id = "1",
                updatedAt = "2024-03-21T10:00:00Z",
                lastMessage = "Hello",
                participant = Profile(id = "p1", username = "user1")
            )
        )
        
        coEvery { SupabaseRepository.getConversations("test_user_id") } returns Result.success(mockConversations)

        // Act
        viewModel.loadConversations()

        // Assert
        assertThat(viewModel.conversations).isEqualTo(mockConversations)
        assertThat(viewModel.isLoadingConversations).isFalse()
        assertThat(viewModel.errorMessage).isNull()
    }

    @Test
    fun `loadConversations sets error message on failure`() = runTest {
        // Arrange
        val errorMsg = "API Error"
        coEvery { SupabaseRepository.getConversations("test_user_id") } returns Result.failure(Exception(errorMsg))

        // Act
        viewModel.loadConversations()

        // Assert
        assertThat(viewModel.conversations).isEmpty()
        assertThat(viewModel.isLoadingConversations).isFalse()
        assertThat(viewModel.errorMessage).isEqualTo(errorMsg)
    }

    @Test
    fun `loadConversations sets error if not logged in`() = runTest {
        // Arrange
        every { SupabaseClient.currentUserId } returns null

        // Act
        viewModel.loadConversations()

        // Assert
        assertThat(viewModel.errorMessage).contains("Not logged in")
        assertThat(viewModel.isLoadingConversations).isFalse()
    }
}
