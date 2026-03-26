package com.loopchat.app.data

import com.loopchat.app.BuildConfig
import com.loopchat.app.data.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import com.loopchat.app.data.local.LoopChatDatabase
import com.loopchat.app.data.local.entities.toEntity

/**
 * Repository for Supabase REST API data operations
 */
object SupabaseRepository {
    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    // Profile cache to avoid N+1 queries when polling for new messages
    private val profileCache = mutableMapOf<String, Profile>()

    /**
     * Get a sender profile, using cache first to avoid repeated API calls
     */
    private suspend fun getCachedProfile(userId: String, accessToken: String): Profile? {
        profileCache[userId]?.let { return it }
        val profile = fetchProfileForCache(userId, accessToken)
        profile?.let { profileCache[userId] = it }
        return profile
    }

    private suspend fun fetchProfileForCache(userId: String, accessToken: String): Profile? {
        return try {
            val profileResponse = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                parameter("select", "id,user_id,full_name,username,avatar_url")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            if (profileResponse.status.isSuccess()) {
                try { profileResponse.body() } catch (e: Exception) { null }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retrieve a user's RSA Public Key from Supabase for E2EE message encryption.
     */
    suspend fun getPublicKey(userId: String): Result<String> {
        val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = httpClient.get("$supabaseUrl/rest/v1/user_public_keys") {
                parameter("select", "public_key")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            if (response.status.isSuccess()) {
                val resultData = response.body<Map<String, String>>()
                val publicKey = resultData["public_key"]
                if (publicKey != null) {
                    Result.success(publicKey)
                } else {
                    Result.failure(Exception("Public key field missing"))
                }
            } else {
                Result.failure(Exception("Public key not found for user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch conversations where the current user is a participant
     */
    suspend fun getConversations(userId: String): Result<List<ConversationWithParticipant>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))

            // Step 1: Get conversation IDs where current user is a participant
            val participantsResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "conversation_id")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!participantsResponse.status.isSuccess()) {
                val error = participantsResponse.bodyAsText()
                android.util.Log.e("SupabaseRepo", "Failed to fetch participant conversations: $error")
                return Result.failure(Exception("Failed to fetch participant conversations: $error"))
            }

            val myConversationIds: List<ConversationParticipantId> = participantsResponse.body()
            android.util.Log.d("SupabaseRepo", "User $userId is participant in ${myConversationIds.size} conversations")

            if (myConversationIds.isEmpty()) {
                return Result.success(emptyList())
            }

            val convIdList = myConversationIds.map { it.conversation_id }.joinToString(",")

            // Step 2: Fetch ONLY conversations the user participates in
            val conversationsResponse = httpClient.get("$supabaseUrl/rest/v1/conversations") {
                parameter("select", "id,updated_at,is_group,group_id,groups(name,avatar_url)")
                parameter("id", "in.($convIdList)")
                parameter("order", "updated_at.desc")
                parameter("limit", "50")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!conversationsResponse.status.isSuccess()) {
                val error = conversationsResponse.bodyAsText()
                android.util.Log.e("SupabaseRepo", "Failed to fetch conversations: $error")
                return Result.failure(Exception("Failed to fetch conversations: $error"))
            }

            val allConversations: List<ConversationBasic> = conversationsResponse.body()
            android.util.Log.d("SupabaseRepo", "Found ${allConversations.size} conversations for user")

            if (allConversations.isEmpty()) {
                return Result.success(emptyList())
            }

            // For each conversation, get participants and profile + last message
            val conversations = allConversations.mapNotNull { conv ->
                val participant = if (conv.is_group) null else getAnyParticipantProfile(conv.id, userId, accessToken)
                val lastMsg = getLastMessage(conv.id, accessToken)
                ConversationWithParticipant(
                    id = conv.id,
                    updatedAt = conv.updated_at,
                    lastMessage = lastMsg?.first,
                    lastMessageType = lastMsg?.second,
                    participant = participant,
                    isGroup = conv.is_group,
                    groupId = conv.group_id,
                    groupName = conv.groups?.name,
                    groupAvatarUrl = conv.groups?.avatar_url
                )
            }

            android.util.Log.d("SupabaseRepo", "Returning ${conversations.size} conversations")
            Result.success(conversations)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Exception in getConversations: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get the last message for a conversation (content + message_type)
     * Returns Pair(content, messageType) or null if no messages
     */
    private suspend fun getLastMessage(
        conversationId: String,
        accessToken: String
    ): Pair<String, String>? {
        return try {
            val response = httpClient.get("$supabaseUrl/rest/v1/messages") {
                parameter("select", "content,message_type")
                parameter("conversation_id", "eq.$conversationId")
                parameter("order", "created_at.desc")
                parameter("limit", "1")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            if (!response.status.isSuccess()) return null
            val messages: List<Map<String, String>> = response.body()
            val msg = messages.firstOrNull() ?: return null
            val content = msg["content"] ?: ""
            val messageType = msg["message_type"] ?: "text"
            Pair(content, messageType)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Failed to fetch last message for $conversationId: ${e.message}")
            null
        }
    }

    /**
     * Get any participant's profile for a conversation (other than current user if possible)
     */
    private suspend fun getAnyParticipantProfile(
        conversationId: String,
        currentUserId: String,
        accessToken: String
    ): Profile? {
        return try {
            // Get all participants in this conversation
            val participantsResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "user_id")
                parameter("conversation_id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!participantsResponse.status.isSuccess()) return null
            val participants: List<UserIdOnly> = participantsResponse.body()

            // Safety check: verify current user is actually a participant
            val isParticipant = participants.any { it.user_id == currentUserId }
            if (!isParticipant) {
                android.util.Log.w("SupabaseRepo", "User $currentUserId is NOT a participant of conversation $conversationId — skipping")
                return null
            }
            
            // Prefer other user, but take any if none
            val targetUserId = participants.find { it.user_id != currentUserId }?.user_id 
                ?: participants.firstOrNull()?.user_id
                ?: return null

            // Get profile
            val profileResponse = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                parameter("select", "id,user_id,full_name,username,avatar_url,bio")
                parameter("user_id", "eq.$targetUserId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }

            if (profileResponse.status.isSuccess()) {
                try { profileResponse.body() } catch (e: Exception) { null }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getConversationWithParticipant(
        conversationId: String,
        currentUserId: String,
        accessToken: String
    ): ConversationWithParticipant? {
        return try {
            // Get conversation
            val convResponse = httpClient.get("$supabaseUrl/rest/v1/conversations") {
                parameter("select", "id,updated_at")
                parameter("id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }

            if (!convResponse.status.isSuccess()) return null
            val conversation: ConversationBasic = convResponse.body()

            // Get other participant
            val otherParticipantResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "user_id")
                parameter("conversation_id", "eq.$conversationId")
                parameter("user_id", "neq.$currentUserId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!otherParticipantResponse.status.isSuccess()) return null
            val otherParticipants: List<UserIdOnly> = otherParticipantResponse.body()
            val otherUserId = otherParticipants.firstOrNull()?.user_id ?: return null

            // Get profile of other participant
            val profileResponse = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                parameter("select", "id,user_id,full_name,username,avatar_url,status")
                parameter("user_id", "eq.$otherUserId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }

            val profile: Profile? = if (profileResponse.status.isSuccess()) {
                try { profileResponse.body() } catch (e: Exception) { null }
            } else null

            ConversationWithParticipant(
                id = conversation.id,
                updatedAt = conversation.updated_at,
                lastMessage = null,
                participant = profile
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch contacts for a user
     */
    suspend fun getContacts(userId: String): Result<List<ContactWithProfile>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))

            val contactsResponse = httpClient.get("$supabaseUrl/rest/v1/contacts") {
                parameter("select", "id,user_id,contact_user_id,nickname,created_at")
                parameter("user_id", "eq.$userId")
                parameter("order", "created_at.desc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!contactsResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to fetch contacts"))
            }

            val contacts: List<Contact> = contactsResponse.body()
            
            // Fetch profiles for each contact
            val contactsWithProfiles = contacts.mapNotNull { contact ->
                val profileResponse = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                    parameter("select", "id,user_id,full_name,username,avatar_url,status")
                    parameter("user_id", "eq.${contact.contact_user_id}")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.pgrst.object+json")
                }

                val profile: Profile? = if (profileResponse.status.isSuccess()) {
                    try { profileResponse.body() } catch (e: Exception) { null }
                } else null

                ContactWithProfile(
                    id = contact.id,
                    userId = contact.user_id,
                    contactUserId = contact.contact_user_id,
                    nickname = contact.nickname,
                    createdAt = contact.created_at,
                    profile = profile
                )
            }

            Result.success(contactsWithProfiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Find registered users by their phone numbers
     */
    suspend fun findUsersByPhoneNumbers(phoneNumbers: List<String>): Result<List<Profile>> {
        return try {
            if (phoneNumbers.isEmpty()) return Result.success(emptyList())
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            
            val allProfiles = mutableListOf<Profile>()
            val chunks = phoneNumbers.chunked(50) // 50 numbers per request to avoid URL length limits
            
            for (chunk in chunks) {
                val phoneListStr = chunk.joinToString(",") { it }
                val response = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                    parameter("select", "id,user_id,full_name,username,avatar_url,phone")
                    parameter("phone", "in.($phoneListStr)")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                }
                
                if (response.status.isSuccess()) {
                    val profiles: List<Profile> = response.body()
                    allProfiles.addAll(profiles)
                }
            }
            Result.success(allProfiles.distinctBy { it.id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch messages for a conversation
     */
    suspend fun getMessages(conversationId: String): Result<List<MessageWithSender>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))

            val messagesResponse = httpClient.get("$supabaseUrl/rest/v1/messages") {
                parameter("select", "id,content,conversation_id,sender_id,created_at,media_url,message_type,expires_at")
                parameter("conversation_id", "eq.$conversationId")
                parameter("order", "created_at.asc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!messagesResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to fetch messages"))
            }

            val messages: List<Message> = messagesResponse.body()
            
            // Fetch sender profiles using cache
            val messagesWithSenders = messages.map { message ->
                val sender = getCachedProfile(message.senderId, accessToken)
                MessageWithSender(
                    id = message.id,
                    content = message.content,
                    conversationId = message.conversationId,
                    senderId = message.senderId,
                    createdAt = message.createdAt,
                    sender = sender,
                    mediaUrl = message.mediaUrl,
                    messageType = message.messageType,
                    expiresAt = message.expiresAt
                )
            }

            Result.success(messagesWithSenders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch messages from remote and sync them to Room Local Database
     */
    suspend fun syncMessages(conversationId: String, context: android.content.Context): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))

            // Fetch all remote messages for the conversation
            val messagesResponse = httpClient.get("$supabaseUrl/rest/v1/messages") {
                parameter("select", "id,content,conversation_id,sender_id,created_at,media_url,message_type,expires_at")
                parameter("conversation_id", "eq.$conversationId")
                parameter("order", "created_at.asc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!messagesResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to fetch messages"))
            }

            val messages: List<Message> = messagesResponse.body()
            
            // Insert into the local database
            val db = LoopChatDatabase.getDatabase(context)
            if (messages.isNotEmpty()) {
                val messageEntities = messages.map { it.toEntity() }
                db.messageDao().insertMessages(messageEntities)
            }

            // Sync profiles of senders to User table
            val uniqueSenderIds = messages.map { it.senderId }.distinct()
            uniqueSenderIds.forEach { senderId ->
                val profile = getCachedProfile(senderId, accessToken)
                profile?.let { p ->
                    db.userDao().insertUser(p.toEntity())
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch only new messages since a given timestamp (for efficient polling)
     * Uses profile cache to avoid re-fetching known senders
     */
    suspend fun getNewMessages(conversationId: String, since: String): Result<List<MessageWithSender>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))

            val messagesResponse = httpClient.get("$supabaseUrl/rest/v1/messages") {
                parameter("select", "id,content,conversation_id,sender_id,created_at,media_url,message_type,expires_at")
                parameter("conversation_id", "eq.$conversationId")
                parameter("created_at", "gt.$since")
                parameter("order", "created_at.asc")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!messagesResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to fetch new messages"))
            }

            val messages: List<Message> = messagesResponse.body()
            if (messages.isEmpty()) {
                return Result.success(emptyList())
            }

            // Use cached profiles for known senders
            val messagesWithSenders = messages.map { message ->
                val sender = getCachedProfile(message.senderId, accessToken)
                MessageWithSender(
                    id = message.id,
                    content = message.content,
                    conversationId = message.conversationId,
                    senderId = message.senderId,
                    createdAt = message.createdAt,
                    sender = sender,
                    mediaUrl = message.mediaUrl,
                    messageType = message.messageType,
                    expiresAt = message.expiresAt
                )
            }

            Result.success(messagesWithSenders)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send a message
     */
    suspend fun sendMessage(
        conversationId: String, 
        content: String, 
        replyToMessageId: String? = null,
        mediaUrl: String? = null,
        messageType: String = "text",
        expiresAt: String? = null
    ): Result<Message> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val senderId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))

            val response = httpClient.post("$supabaseUrl/rest/v1/messages") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(SendMessageRequest(
                    conversation_id = conversationId,
                    sender_id = senderId,
                    content = content,
                    reply_to_message_id = replyToMessageId,
                    media_url = mediaUrl,
                    message_type = messageType,
                    expiresAt = expiresAt
                ))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                android.util.Log.e("SupabaseRepo", "Failed to send message: status=${response.status}, body=$errorBody")
                return Result.failure(Exception("Failed to send message (${response.status}): $errorBody"))
            }

            val messages: List<Message> = response.body()

            // Bump conversation updated_at so it moves to top of chat list
            try {
                httpClient.request("$supabaseUrl/rest/v1/conversations") {
                    method = io.ktor.http.HttpMethod.Patch
                    parameter("id", "eq.$conversationId")
                    contentType(ContentType.Application.Json)
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                    setBody(mapOf("updated_at" to java.time.Instant.now().toString()))
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseRepo", "Failed to update conversation timestamp: ${e.message}")
            }

            Result.success(messages.first())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get conversation participants with their profiles
     */
    suspend fun getConversationParticipants(conversationId: String): Result<List<Profile>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))

            val participantsResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "user_id")
                parameter("conversation_id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!participantsResponse.status.isSuccess()) {
                return Result.failure(Exception("Failed to fetch participants"))
            }

            val participants: List<UserIdOnly> = participantsResponse.body()
            val profiles = mutableListOf<Profile>()
            
            for (userId in participants.map { it.user_id }) {
                val profileResponse = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                    parameter("select", "id,user_id,full_name,username,avatar_url,phone,status")
                    parameter("user_id", "eq.$userId")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.pgrst.object+json")
                }

                if (profileResponse.status.isSuccess()) {
                    try {
                        profiles.add(profileResponse.body())
                    } catch (e: Exception) {
                        android.util.Log.e("SupabaseRepo", "Error: ${e.message}")
                    }
                }
            }

            Result.success(profiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search users by name, phone, or email
     * Excludes current user and users with existing conversations
     */
    suspend fun searchUsers(query: String): Result<List<Profile>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val currentUserId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))

            // Search by full_name or phone
            val response = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                parameter("select", "id,user_id,full_name,username,avatar_url,phone,status")
                parameter("or", "(full_name.ilike.*$query*,phone.ilike.*$query*,username.ilike.*$query*)")
                parameter("limit", "50")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Search failed"))
            }

            val allProfiles: List<Profile> = response.body()
            
            // Get list of user IDs with existing conversations
            val existingConvUserIds = getExistingConversationUserIds(currentUserId, accessToken)
            
            // Filter out current user and users with existing conversations
            val filteredProfiles = allProfiles.filter { profile ->
                profile.userId != currentUserId && profile.userId !in existingConvUserIds
            }
            
            Result.success(filteredProfiles)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Search error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get list of user IDs that current user already has conversations with
     */
    private suspend fun getExistingConversationUserIds(currentUserId: String, accessToken: String): Set<String> {
        return try {
            // Get all conversation IDs for current user
            val myConvsResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "conversation_id")
                parameter("user_id", "eq.$currentUserId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (!myConvsResponse.status.isSuccess()) return emptySet()
            val myConvs: List<ConversationParticipantId> = myConvsResponse.body()
            
            if (myConvs.isEmpty()) return emptySet()
            
            val conversationIds = myConvs.map { it.conversation_id }
            
            // Get all participants in these conversations (excluding current user)
            val participantsResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "user_id")
                parameter("conversation_id", "in.(${conversationIds.joinToString(",")})")
                parameter("user_id", "neq.$currentUserId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (!participantsResponse.status.isSuccess()) return emptySet()
            val participants: List<UserIdOnly> = participantsResponse.body()
            
            participants.map { it.user_id }.toSet()
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Error getting existing conversation users: ${e.message}")
            emptySet()
        }
    }

    suspend fun getConversation(conversationId: String): Result<ConversationBasic> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.get("$supabaseUrl/rest/v1/conversations") {
                parameter("select", "id,updated_at,is_group,group_id,groups(name,avatar_url)")
                parameter("id", "eq.$conversationId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }
            
            if (response.status.isSuccess()) {
                val conversation: ConversationBasic = response.body()
                Result.success(conversation)
            } else {
                Result.failure(Exception("Failed to fetch conversation: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncContacts(phoneNumbers: List<String>): Result<List<com.loopchat.app.data.models.Profile>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            
            val response = httpClient.post("$supabaseUrl/functions/v1/sync-contacts") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf("contacts" to phoneNumbers))
            }
            
            if (response.status.isSuccess()) {
                val jsonBody = response.bodyAsText()
                val jsonObject = Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonBody).jsonObject
                
                val profilesArray = jsonObject["matched_profiles"]?.jsonArray
                if (profilesArray != null) {
                    val decoded = Json { ignoreUnknownKeys = true }.decodeFromJsonElement<List<com.loopchat.app.data.models.Profile>>(profilesArray)
                    Result.success(decoded)
                } else {
                    Result.success(emptyList())
                }
            } else {
                Result.failure(Exception("Failed to sync contacts: \${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create or get existing conversation with a user
     */
    suspend fun createOrGetConversation(otherUserId: String): Result<String> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val currentUserId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))

            // First, check if conversation already exists between these users
            val existingConv = findExistingConversation(currentUserId, otherUserId, accessToken)
            if (existingConv != null) {
                return Result.success(existingConv)
            }

            // Create new conversation
            val createConvResponse = httpClient.post("$supabaseUrl/rest/v1/conversations") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf("is_group" to false))
            }

            if (!createConvResponse.status.isSuccess()) {
                val error = createConvResponse.bodyAsText()
                return Result.failure(Exception("Failed to create conversation: $error"))
            }

            val conversations: List<ConversationBasic> = createConvResponse.body()
            val conversationId = conversations.firstOrNull()?.id
                ?: return Result.failure(Exception("No conversation ID returned"))

            // Add both participants
            val addParticipantsResponse = httpClient.post("$supabaseUrl/rest/v1/conversation_participants") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(listOf(
                    mapOf("conversation_id" to conversationId, "user_id" to currentUserId),
                    mapOf("conversation_id" to conversationId, "user_id" to otherUserId)
                ))
            }

            if (!addParticipantsResponse.status.isSuccess()) {
                val error = addParticipantsResponse.bodyAsText()
                return Result.failure(Exception("Failed to add participants: $error"))
            }

            Result.success(conversationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun findExistingConversation(
        userId1: String,
        userId2: String,
        accessToken: String
    ): String? {
        return try {
            // Get all conversations for user1
            val user1ConvsResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "conversation_id")
                parameter("user_id", "eq.$userId1")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!user1ConvsResponse.status.isSuccess()) return null
            val user1Convs: List<ConversationParticipantId> = user1ConvsResponse.body()
            if (user1Convs.isEmpty()) return null

            // Get all conversations for user2
            val user2ConvsResponse = httpClient.get("$supabaseUrl/rest/v1/conversation_participants") {
                parameter("select", "conversation_id")
                parameter("user_id", "eq.$userId2")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }

            if (!user2ConvsResponse.status.isSuccess()) return null
            val user2Convs: List<ConversationParticipantId> = user2ConvsResponse.body()

            // Find common conversations shared by both users
            val user1ConvIds = user1Convs.map { it.conversation_id }.toSet()
            val commonConvIds = user2Convs.map { it.conversation_id }.filter { it in user1ConvIds }

            if (commonConvIds.isEmpty()) return null

            // Verify the common conversation is a 1:1 chat (not a group)
            for (convId in commonConvIds) {
                val convResponse = httpClient.get("$supabaseUrl/rest/v1/conversations") {
                    parameter("select", "id,is_group")
                    parameter("id", "eq.$convId")
                    parameter("is_group", "eq.false")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                }
                if (convResponse.status.isSuccess()) {
                    val convs: List<ConversationBasic> = convResponse.body()
                    if (convs.isNotEmpty()) return convs.first().id
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get current user's profile
     */
    suspend fun getCurrentUserProfile(): Result<Profile> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))

            val response = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                parameter("select", "*")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }

            if (!response.status.isSuccess()) {
                return Result.failure(Exception("Failed to fetch profile"))
            }

            val profile: Profile = response.body()
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a user's profile by their user_id (tries both user_id and id columns)
     */
    suspend fun getProfileById(userId: String): Result<Profile> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))

            // First try by user_id (auth user ID)
            var response = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                parameter("select", "id,user_id,full_name,username,avatar_url,bio,phone,status")
                parameter("user_id", "eq.$userId")
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                header("Accept", "application/vnd.pgrst.object+json")
            }

            // If user_id lookup fails, try by profile id
            if (!response.status.isSuccess()) {
                response = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                    parameter("select", "id,user_id,full_name,username,avatar_url,bio,phone,status")
                    parameter("id", "eq.$userId")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.pgrst.object+json")
                }
            }

            if (!response.status.isSuccess()) {
                android.util.Log.e("SupabaseRepo", "Failed to fetch profile for ID: $userId")
                return Result.failure(Exception("Failed to fetch profile"))
            }

            val profile: Profile = response.body()
            android.util.Log.d("SupabaseRepo", "Fetched profile: ${profile.fullName ?: profile.username}")
            Result.success(profile)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Error fetching profile: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update current user's profile
     */
    suspend fun updateProfile(
        fullName: String? = null,
        username: String? = null,
        bio: String? = null,
        phone: String? = null
    ): Result<Boolean> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))

            val updateData = mutableMapOf<String, String?>()
            fullName?.let { if (it.isNotBlank()) updateData["full_name"] = it }
            username?.let { if (it.isNotBlank()) updateData["username"] = it }
            bio?.let { updateData["bio"] = it }
            phone?.let { if (it.isNotBlank()) updateData["phone"] = it }

            if (updateData.isEmpty()) {
                return Result.success(true) // Nothing to update
            }

            val response = httpClient.request("$supabaseUrl/rest/v1/profiles") {
                method = io.ktor.http.HttpMethod.Patch
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                parameter("user_id", "eq.$userId")
                setBody(updateData)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                android.util.Log.e("SupabaseRepo", "Failed to update profile: $errorBody")
                return Result.failure(Exception("Failed to update profile"))
            }

            android.util.Log.d("SupabaseRepo", "Profile updated successfully")
            Result.success(true)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Error updating profile: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Add a contact
     */
    suspend fun addContact(contactUserId: String, nickname: String? = null): Result<Boolean> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))

            val response = httpClient.post("$supabaseUrl/rest/v1/contacts") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
                setBody(AddContactRequest(
                    user_id = userId,
                    contact_user_id = contactUserId,
                    nickname = nickname
                ))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                if (errorBody.contains("23505")) {
                    return Result.failure(Exception("Contact already exists"))
                }
                return Result.failure(Exception("Failed to add contact"))
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Fetch call history for the current user
     */
    suspend fun getCallHistory(offset: Int = 0, limit: Int = 50): Result<List<CallWithProfile>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() ?: return Result.failure(Exception("Not authenticated"))
            val userId = SupabaseClient.currentUserId ?: return Result.failure(Exception("No user ID"))
            
            // Fetch calls where user is caller or callee
            val callsResponse = httpClient.get("$supabaseUrl/rest/v1/calls") {
                parameter("select", "id,caller_id,callee_id,call_type,status,created_at,ended_at")
                parameter("or", "(caller_id.eq.$userId,callee_id.eq.$userId)")
                parameter("order", "created_at.desc")
                parameter("limit", limit.toString())
                parameter("offset", offset.toString())
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (!callsResponse.status.isSuccess()) {
                val error = callsResponse.bodyAsText()
                android.util.Log.e("SupabaseRepo", "Failed to fetch calls: $error")
                return Result.failure(Exception("Failed to fetch call history"))
            }
            
            val calls: List<Call> = callsResponse.body()
            android.util.Log.d("SupabaseRepo", "Found ${calls.size} calls")
            
            if (calls.isEmpty()) {
                return Result.success(emptyList())
            }
            
            // For each call, get the other user's profile
            val callsWithProfiles = calls.mapNotNull { call ->
                val isOutgoing = call.callerId == userId
                val otherUserId = if (isOutgoing) (call.calleeId ?: call.groupId) else call.callerId
                
                // Fetch profile of other user
                val profileResponse = httpClient.get("$supabaseUrl/rest/v1/profiles") {
                    parameter("select", "id,user_id,full_name,username,avatar_url")
                    parameter("user_id", "eq.$otherUserId")
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $accessToken")
                    header("Accept", "application/vnd.pgrst.object+json")
                }
                
                val otherProfile: Profile? = if (profileResponse.status.isSuccess()) {
                    try { profileResponse.body() } catch (e: Exception) { null }
                } else null
                
                CallWithProfile(
                    id = call.id,
                    callerId = call.callerId,
                    calleeId = call.calleeId ?: "",
                    callType = call.callType,
                    status = call.status,
                    createdAt = call.createdAt,
                    endedAt = call.endedAt,
                    isOutgoing = isOutgoing,
                    otherUserProfile = otherProfile
                )
            }
            
            Result.success(callsWithProfiles)
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Error fetching call history: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Upload profile picture to Supabase Storage
     */
    suspend fun uploadProfilePicture(context: android.content.Context, imageUri: android.net.Uri): Result<String> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val currentUserId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("User ID not found"))
            
            // Read image bytes from URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("Cannot open image"))
            val imageBytes = inputStream.readBytes()
            inputStream.close()
            
            android.util.Log.d("SupabaseRepo", "Uploading profile picture: ${imageBytes.size} bytes")
            
            // Generate unique filename
            val fileName = "avatar_${currentUserId}_${System.currentTimeMillis()}.jpg"
            
            // Upload to Supabase Storage using POST with upsert
            val response = httpClient.post("${BuildConfig.SUPABASE_URL}/storage/v1/object/avatars/$fileName") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                header("x-upsert", "true") // Allow overwriting existing files
                contentType(ContentType.Image.JPEG)
                setBody(imageBytes)
            }
            
            android.util.Log.d("SupabaseRepo", "Upload response: ${response.status}")
            
            if (response.status.isSuccess()) {
                // Get public URL
                val publicUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/avatars/$fileName"
                android.util.Log.d("SupabaseRepo", "Upload success, URL: $publicUrl")
                
                // Update profile with new avatar URL
                val updateResult = updateAvatarUrl(publicUrl)
                if (updateResult.isSuccess) {
                    Result.success(publicUrl)
                } else {
                    Result.failure(updateResult.exceptionOrNull() ?: Exception("Failed to update profile"))
                }
            } else {
                val errorBody = response.bodyAsText()
                android.util.Log.e("SupabaseRepo", "Upload failed: ${response.status} - $errorBody")
                Result.failure(Exception("Upload failed: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Error uploading profile picture: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Update avatar URL in profile
     */
    suspend fun updateAvatarUrl(avatarUrl: String): Result<Unit> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val currentUserId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("User ID not found"))
            
            val response = httpClient.request("${BuildConfig.SUPABASE_URL}/rest/v1/profiles") {
                method = HttpMethod.Patch
                contentType(ContentType.Application.Json)
                parameter("user_id", "eq.$currentUserId")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                setBody(mapOf("avatar_url" to avatarUrl))
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to update avatar"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get stories from last 24 hours grouped by user
     */
    suspend fun getStories(): Result<List<StoryWithProfile>> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            
            // Get stories from last 24 hours
            val oneDayAgo = java.time.Instant.now().minusSeconds(24 * 60 * 60).toString()
            
            val response = httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/stories") {
                parameter("select", "id,user_id,media_url,media_type,caption,created_at,expires_at")
                parameter("created_at", "gte.$oneDayAgo")
                parameter("order", "created_at.asc")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
            }
            
            if (response.status.isSuccess()) {
                val stories: List<com.loopchat.app.data.models.Story> = response.body()
                
                // Fetch profiles for each unique user
                val userIds = stories.map { it.userId }.distinct()
                val profiles = mutableMapOf<String, Profile>()
                
                for (userId in userIds) {
                    getProfileById(userId).onSuccess { profile ->
                        profiles[userId] = profile
                    }
                }
                
                val storiesWithProfiles = stories.map { story ->
                    StoryWithProfile(
                        story = story,
                        userProfile = profiles[story.userId]
                    )
                }
                
                Result.success(storiesWithProfiles)
            } else {
                Result.failure(Exception("Failed to fetch stories"))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Error fetching stories: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Create a new story
     */
    suspend fun createStory(context: android.content.Context, imageUri: android.net.Uri, caption: String? = null): Result<String> {
        return try {
            val accessToken = SupabaseClient.getAccessToken() 
                ?: return Result.failure(Exception("Not authenticated"))
            val currentUserId = SupabaseClient.currentUserId 
                ?: return Result.failure(Exception("User ID not found"))
            
            // Read image bytes
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("Cannot open image"))
            val imageBytes = inputStream.readBytes()
            inputStream.close()
            
            android.util.Log.d("SupabaseRepo", "Creating story: ${imageBytes.size} bytes")
            
            // Generate filename
            val fileName = "story_${currentUserId}_${System.currentTimeMillis()}.jpg"
            
            // Upload to Supabase Storage (stories bucket) using POST with upsert
            val uploadResponse = httpClient.post("${BuildConfig.SUPABASE_URL}/storage/v1/object/stories/$fileName") {
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                header("x-upsert", "true") // Allow overwriting existing files
                contentType(ContentType.Image.JPEG)
                setBody(imageBytes)
            }
            
            android.util.Log.d("SupabaseRepo", "Story upload response: ${uploadResponse.status}")
            
            if (!uploadResponse.status.isSuccess()) {
                val errorBody = uploadResponse.bodyAsText()
                android.util.Log.e("SupabaseRepo", "Story upload failed: ${uploadResponse.status} - $errorBody")
                return Result.failure(Exception("Failed to upload story image: $errorBody"))
            }
            
            val mediaUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/stories/$fileName"
            val expiresAt = java.time.Instant.now().plusSeconds(24 * 60 * 60).toString()
            
            // Create story record
            val storyResponse = httpClient.post("${BuildConfig.SUPABASE_URL}/rest/v1/stories") {
                contentType(ContentType.Application.Json)
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "return=representation")
                setBody(mapOf(
                    "user_id" to currentUserId,
                    "media_url" to mediaUrl,
                    "media_type" to "image",
                    "caption" to (caption ?: ""),
                    "expires_at" to expiresAt
                ))
            }
            
            if (storyResponse.status.isSuccess()) {
                Result.success(mediaUrl)
            } else {
                Result.failure(Exception("Failed to create story record"))
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseRepo", "Error creating story: ${e.message}")
            Result.failure(e)
        }
    }
}

// Helper data classes for API responses
@Serializable
data class ConversationParticipantId(val conversation_id: String)

@Serializable
data class UserIdOnly(val user_id: String)

@Serializable
data class GroupBasic(
    val name: String? = null,
    val avatar_url: String? = null
)

@Serializable
data class ConversationBasic(
    val id: String,
    val updated_at: String? = null,
    val is_group: Boolean = false,
    val group_id: String? = null,
    val groups: GroupBasic? = null
)

@Serializable
data class Contact(
    val id: String,
    val user_id: String,
    val contact_user_id: String,
    val nickname: String? = null,
    val created_at: String? = null
)

@Serializable
data class SendMessageRequest(
    val conversation_id: String,
    val sender_id: String,
    val content: String,
    val reply_to_message_id: String? = null,
    val media_url: String? = null,
    val message_type: String = "text",
    @SerialName("expires_at")
    val expiresAt: String? = null
)

@Serializable
data class CreateConversationRequest(
    val user1_id: String,
    val user2_id: String
)

@Serializable
data class AddContactRequest(
    val user_id: String,
    val contact_user_id: String,
    val nickname: String? = null
)

// UI-ready data classes
data class ConversationWithParticipant(
    val id: String,
    val updatedAt: String?,
    val lastMessage: String?,
    val lastMessageType: String? = null,
    val participant: Profile?,
    val isGroup: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val groupAvatarUrl: String? = null
)

data class ContactWithProfile(
    val id: String,
    val userId: String,
    val contactUserId: String,
    val nickname: String?,
    val createdAt: String?,
    val profile: Profile?
)

data class MessageWithSender(
    val id: String,
    val content: String,
    val conversationId: String,
    val senderId: String,
    val createdAt: String?,
    val sender: Profile?,
    // Media fields
    val mediaUrl: String? = null,
    val messageType: String = "text",
    // New fields for enhanced messaging
    val editedAt: String? = null,
    val replyToMessageId: String? = null,
    val forwarded: Boolean? = null,
    val deletedForEveryone: Boolean? = null,
    val expiresAt: String? = null,
    val deletedAt: String? = null,
    val isRead: Boolean = false,
    val status: String = "synced"
)

/**
 * Call record with profile information for UI display
 */
data class CallWithProfile(
    val id: String,
    val callerId: String,
    val calleeId: String,
    val callType: String,
    val status: String,
    val createdAt: String?,
    val endedAt: String?,
    val isOutgoing: Boolean,
    val otherUserProfile: Profile?
)

/**
 * Story with user profile for UI display
 */
data class StoryWithProfile(
    val story: com.loopchat.app.data.models.Story,
    val userProfile: Profile?
)
