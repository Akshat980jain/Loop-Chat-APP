# 🔄 Loop Chat — Android Blueprint

> **Version**: 1.0 (March 2026)
> **Package**: `com.loopchat.app` | **Min SDK**: 26 | **Target SDK**: 34
> **Architecture**: MVVM + Room SSOT + Compose UI + Supabase BaaS

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Current Feature Inventory](#2-current-feature-inventory)
3. [Codebase Map](#3-codebase-map)
4. [Technology Stack](#4-technology-stack)
5. [Data Layer Deep Dive](#5-data-layer-deep-dive)
6. [Feature Status Matrix](#6-feature-status-matrix)
7. [Future Implementation Roadmap](#7-future-implementation-roadmap)
8. [Backend (Supabase) Requirements](#8-backend-supabase-requirements)
9. [Testing Strategy](#9-testing-strategy)
10. [Release & Distribution](#10-release--distribution)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                      │
│  Jetpack Compose Screens + Components (Electric Noir Theme) │
│  ├─ 16 Screens (Auth, Home, Chat, Call, Profile, etc.)      │
│  ├─ 17 Reusable Components (Bubbles, Avatars, Polls, etc.)  │
│  └─ Navigation via Compose NavHost (sealed Screen class)    │
├─────────────────────────────────────────────────────────────┤
│                      VIEWMODEL LAYER                        │
│  7 ViewModels (Auth, Home, Chat, EnhancedChat, Group, etc.) │
│  ├─ State management via mutableStateOf                     │
│  └─ Business logic, coroutines, polling                     │
├─────────────────────────────────────────────────────────────┤
│                        DATA LAYER                           │
│  ├─ Room DB (SSOT) — 4 DAOs, 5 Entities                    │
│  ├─ SupabaseRepository (REST API via Ktor)                  │
│  ├─ SupabaseRealtimeClient (WebSocket)                      │
│  ├─ CryptoManager (E2E Encryption)                          │
│  ├─ GroupRepository, PrivacySecurityRepository              │
│  ├─ MediaUploadManager, VoiceRecorder                       │
│  └─ DataStore (Session Persistence)                         │
├─────────────────────────────────────────────────────────────┤
│                     SERVICES LAYER                          │
│  ├─ CallService (Foreground — Daily.co VoIP)                │
│  ├─ LoopChatMessagingService (FCM Push Notifications)       │
│  └─ IncomingCallManager (Realtime Call Listener)            │
├─────────────────────────────────────────────────────────────┤
│                    EXTERNAL SERVICES                        │
│  ├─ Supabase (Auth, Database, Storage, Edge Functions)      │
│  ├─ Daily.co (WebRTC Video/Audio Calling)                   │
│  └─ Firebase Cloud Messaging (Push Notifications)           │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Current Feature Inventory

### ✅ Fully Implemented
| Feature | Key Files | Status |
|---------|-----------|--------|
| **Authentication** (Email Login/Signup) | `AuthScreen.kt`, `AuthViewModel.kt` | ✅ Working |
| **1-on-1 Messaging** (Text, Media, Reactions) | `EnhancedChatScreen.kt`, `EnhancedChatViewModel.kt` | ✅ Working |
| **Real-time Messages** (WebSocket) | `SupabaseRealtimeClient.kt` | ✅ Working |
| **Local Caching** (Room DB SSOT) | `LoopChatDatabase.kt`, DAOs, Entities | ✅ Working |
| **Audio/Video Calling** (Daily.co) | `CallScreen.kt`, `DailyCallManager.kt`, `CallService.kt` | ✅ Working |
| **Incoming Calls** (FCM + Foreground Service) | `IncomingCallActivity.kt`, `IncomingCallManager.kt` | ✅ Working |
| **Push Notifications** (FCM) | `LoopChatMessagingService.kt` | ✅ Working |
| **Contact Management** | `HomeScreen.kt` (Contacts Tab), `ContactsManager.kt` | ✅ Working |
| **User Search & Add** | `SearchComponents.kt`, `HomeViewModel.kt` | ✅ Working |
| **Profile Editing** | `ProfileScreen.kt` | ✅ Working |
| **Story/Status Upload & View** | `HomeScreen.kt` (Stories Strip), `StatusScreen.kt` | ✅ Working |
| **Message Encryption** (Client-side AES) | `CryptoManager.kt` | ✅ Working |
| **Media Sharing** (Image, Video, Document) | `MediaUploadManager.kt`, `MediaComponents.kt` | ✅ Working |
| **Voice Messages** | `VoiceRecorder.kt`, `VoiceMessageBubble.kt`, `VoiceRecordBar.kt` | ✅ Working |
| **Message Reactions** (Emoji) | `EnhancedChatScreen.kt`, `MessagingFeaturesRepository.kt` | ✅ Working |
| **Message Reply / Edit / Delete** | `EnhancedChatViewModel.kt` | ✅ Working |
| **Read Receipts** (Double Checkmark) | `EnhancedChatScreen.kt` | ✅ Working |
| **Typing Indicators** | `EnhancedChatViewModel.kt` | ✅ Working |
| **Online/Offline Presence** | `PresenceIndicator.kt`, `SupabaseRealtimeClient.kt` | ✅ Working |
| **Call History** | `CallHistoryScreen.kt` | ✅ Working |
| **Pin / Archive / Mute Conversations** | `HomeViewModel.kt`, `PrivacySecurityRepository.kt` | ✅ Working |
| **Message Search** | `HomeScreen.kt` (Global Search) | ✅ Working |
| **Privacy Settings** | `PrivacyComponents.kt`, `PrivacySettingsScreen.kt` | ✅ Working |
| **Security Settings** (2FA, Biometric) | `SecurityComponents.kt`, `SecuritySettingsScreen.kt` | ✅ Working |
| **Active Sessions Management** | `ActiveSessionsScreen.kt` | ✅ Working |
| **Blocked Users** | `BlockedUsersScreen.kt`, `BlockedContactsScreen.kt` | ✅ Working |
| **Timestamp Localization** (12h IST) | `HomeComponents.kt`, `EnhancedChatScreen.kt` | ✅ Working |

### 🟡 Partially Implemented (Needs Work)
| Feature | Current State | What's Missing |
|---------|---------------|----------------|
| **Group Chat Creation** | UI + ViewModel connected, backend call wired | Backend `groups` table + Edge Function may not exist |
| **Group Messaging** | Conversation model supports `is_group` | Group-specific messaging flow not tested E2E |
| **Poll Bubbles** | UI renders with placeholder options | Backend poll creation/voting not connected |
| **Media Gallery** | Screen shell exists | No media indexing or filtering logic |
| **QR Code Scanning** | Screen shell exists | No QR code logic or camera integration |
| **Notifications Screen** | Screen shell exists | No notification history fetching |
| **Vanish Mode** | Toggle exists in UI | No auto-delete timer or backend support |
| **Message Forwarding** | Model field (`forwarded`) exists | No forward dialog or conversation picker |
| **Starred Messages** | Toggle works in UI (local state) | No persistence to backend |

### 🔴 Not Yet Implemented
| Feature | Description |
|---------|-------------|
| **End-to-End Encryption (True E2EE)** | Current AES encryption is not key-exchanged per user |
| **Group Video/Audio Calls** | Only 1-on-1 calls supported |
| **Message Scheduling** | Send messages at a future time |
| **Chat Themes / Wallpapers** | Per-conversation theming |
| **Chat Backup & Restore** | Export/import chat history |
| **In-App Payments / Wallet** | Payment integration |
| **Location Sharing** | Real-time or one-time location sharing |
| **Contact Sync** (Phone Book) | Import contacts from device |
| **Multi-Language Support** | i18n/l10n beyond English |
| **Accessibility** | Screen reader, high contrast, font scaling |
| **App Lock** (PIN/Pattern) | App-level lock beyond biometric |
| **Chat Export** (PDF/Text) | Export individual conversations |
| **Admin Panel** (Group Management) | Roles, permissions, moderation tools |

---

## 3. Codebase Map

```
com.loopchat.app/
├── MainActivity.kt                    # Entry point, call navigation data handling
├── IncomingCallActivity.kt            # Separate activity for incoming call UI
├── LoopChatApplication.kt            # Application class, notification channels
│
├── data/                              # DATA LAYER
│   ├── SupabaseClient.kt             # HTTP client, auth, session management
│   ├── SupabaseRepository.kt         # All REST API calls (~1300 lines)
│   ├── SupabaseRepositoryExtensions.kt # Extension functions for repository
│   ├── GroupRepository.kt            # Group CRUD + member management
│   ├── MessagingFeaturesRepository.kt # Reactions, stars, edit, delete, polls
│   ├── PrivacySecurityRepository.kt  # Privacy, security, 2FA, sessions
│   ├── SettingsRepository.kt         # App settings persistence
│   ├── SettingsDataStore.kt          # DataStore wrapper for preferences
│   ├── MediaUploadManager.kt         # File upload to Supabase Storage
│   ├── VoiceRecorder.kt             # MediaRecorder wrapper for voice notes
│   ├── ContactsManager.kt           # Contact management utilities
│   ├── IncomingCallManager.kt        # Realtime call subscription + notifications
│   ├── DailyCallManager.kt          # Daily.co SDK integration
│   ├── CallAudioManager.kt          # AudioManager for call routing
│   ├── CallSoundManager.kt          # Ringtone, DTMF, call sounds
│   │
│   ├── crypto/
│   │   └── CryptoManager.kt         # AES encryption/decryption for messages
│   │
│   ├── realtime/
│   │   └── SupabaseRealtimeClient.kt # WebSocket for messages + presence
│   │
│   ├── models/
│   │   └── Models.kt                 # Serializable models (Profile, Message, Call, Story, etc.)
│   │
│   └── local/                         # ROOM DATABASE (Single Source of Truth)
│       ├── LoopChatDatabase.kt       # @Database definition
│       ├── dao/
│       │   ├── MessageDao.kt         # CRUD for messages table
│       │   ├── UserDao.kt            # CRUD for users table
│       │   ├── ConversationDao.kt    # CRUD for conversations table
│       │   └── GroupDao.kt           # CRUD for groups + group_members tables
│       └── entities/
│           ├── MessageEntity.kt      # Messages table entity
│           ├── UserEntity.kt         # Users table entity
│           ├── ConversationEntity.kt # Conversations table entity
│           ├── GroupEntity.kt        # Groups table entity
│           └── GroupMemberEntity.kt  # Group members table entity
│
├── services/                          # BACKGROUND SERVICES
│   ├── CallService.kt               # Foreground service for active calls
│   └── LoopChatMessagingService.kt   # FCM message handler
│
└── ui/                                # PRESENTATION LAYER
    ├── navigation/
    │   └── Navigation.kt             # NavHost + sealed Screen routes
    │
    ├── theme/
    │   ├── Color.kt                  # Electric Noir color palette
    │   ├── Theme.kt                  # Material3 theme definition
    │   └── Type.kt                   # Typography (Manrope + Inter fonts)
    │
    ├── screens/
    │   ├── AuthScreen.kt             # Login / Signup
    │   ├── HomeScreen.kt             # Chats, Calls, Contacts, Settings tabs
    │   ├── EnhancedChatScreen.kt     # Full chat room (messages, media, polls)
    │   ├── ChatScreen.kt             # Legacy chat screen (kept for reference)
    │   ├── CallScreen.kt             # Active call UI (audio/video)
    │   ├── IncomingCallScreen.kt     # Incoming call accept/reject
    │   ├── CallHistoryScreen.kt      # Call log per contact
    │   ├── ProfileScreen.kt          # User profile editing
    │   ├── GroupCreationScreen.kt    # Create new group with member selection
    │   ├── MediaViewerScreen.kt      # Full-screen image/video viewer
    │   ├── SearchScreen.kt           # Global search (placeholder)
    │   ├── StatusScreen.kt           # Stories / status (placeholder)
    │   ├── NotificationsScreen.kt    # Notification history (placeholder)
    │   ├── MediaGalleryScreen.kt     # Media gallery (placeholder)
    │   ├── QRScanScreen.kt           # QR scanner (placeholder)
    │   └── BlockedContactsScreen.kt  # Blocked contacts list
    │
    ├── components/
    │   ├── HomeComponents.kt         # ConversationItem, formatTime
    │   ├── MessageComponents.kt      # MessageBubble, ReplyPreview, ActionMenu
    │   ├── MediaComponents.kt        # ImageBubble, VideoPlayer, DocumentCard
    │   ├── GradientComponents.kt     # GlassCard, SmallGradientAvatar, etc.
    │   ├── SearchComponents.kt       # SearchDialog, SearchBar
    │   ├── PrivacyComponents.kt      # PrivacySettingsScreen
    │   ├── SecurityComponents.kt     # SecuritySettingsScreen
    │   ├── ActiveSessionsScreen.kt   # Session management UI
    │   ├── PollBubble.kt             # Poll rendering in chat
    │   ├── PollComposerBottomSheet.kt # Poll creation bottom sheet
    │   ├── VoiceMessageBubble.kt     # Audio message UI
    │   ├── VoiceRecordBar.kt         # Voice recording controls
    │   ├── PresenceIndicator.kt      # Online/Offline dot
    │   ├── AudioVisualizer.kt        # Waveform animation
    │   ├── CameraPreview.kt          # CameraX preview for video calls
    │   ├── DailyCallWebView.kt       # WebView-based Daily.co call
    │   └── DailyVideoView.kt         # Native Daily video renderer
    │
    └── viewmodels/
        ├── AuthViewModel.kt          # Login/Signup state + FCM token upload
        ├── HomeViewModel.kt          # Conversations, contacts, calls, search
        ├── ChatViewModel.kt          # Legacy chat ViewModel
        ├── EnhancedChatViewModel.kt  # Full-featured chat ViewModel (~820 lines)
        ├── GroupCreationViewModel.kt  # Group creation + contact fetching
        ├── SettingsViewModel.kt      # Privacy/Security settings
        └── VoiceRecorderViewModel.kt  # Voice recording state management
```

---

## 4. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Language** | Kotlin | 1.9.x |
| **UI Framework** | Jetpack Compose + Material3 | BOM 2023.10.01 |
| **Navigation** | Compose Navigation | 2.7.6 |
| **State Management** | `mutableStateOf` (Compose) | — |
| **Local Database** | Room | 2.6.1 |
| **HTTP Client** | Ktor (Android Engine) | 2.3.7 |
| **WebSocket** | Ktor WebSockets | 2.3.7 |
| **Serialization** | kotlinx.serialization | 1.6.2 |
| **Image Loading** | Coil Compose | 2.5.0 |
| **Video Calling** | Daily.co SDK | 0.18.0 |
| **Camera** | CameraX | 1.3.1 |
| **Push Notifications** | Firebase Cloud Messaging | BOM 32.7.0 |
| **Preferences** | DataStore | 1.0.0 |
| **Permissions** | Accompanist Permissions | 0.32.0 |
| **Media Playback** | Media3 ExoPlayer | 1.2.1 |
| **Code Generation** | KSP (for Room) | — |
| **Build System** | Gradle (Kotlin DSL) | 8.14.x |
| **Backend** | Supabase (PostgreSQL, Auth, Storage, Edge Functions) | — |
| **Design System** | Electric Noir (Custom Dark Theme) | — |
| **Typography** | Manrope (headings) + Inter (body) | Google Fonts |
| **Testing** | JUnit 4, MockK, Turbine, Truth, Robolectric, Espresso | — |

---

## 5. Data Layer Deep Dive

### 5.1 Single Source of Truth (SSOT) Architecture

```
REST API (Supabase) ──sync──▶ Room Database ──observe──▶ UI (Compose)
                                    ▲
WebSocket (Realtime) ──insert──────┘
```

- **Room DB** is the single source of truth for messages, users, and conversations.
- The UI observes `Flow<List<T>>` from Room DAOs — never reads directly from the network.
- Background sync fetches from Supabase REST API and inserts into Room.
- Realtime WebSocket listener inserts new messages directly into Room.

### 5.2 Room Database Schema

| Table | Entity | DAO | Key Fields |
|-------|--------|-----|------------|
| `messages` | `MessageEntity` | `MessageDao` | id, conversationId, senderId, content, messageType, mediaUrl, createdAt, isRead, status |
| `users` | `UserEntity` | `UserDao` | id, username, fullName, avatarUrl, isOnline, lastSeen |
| `conversations` | `ConversationEntity` | `ConversationDao` | id, name, lastMessage, updatedAt, isGroup |
| `groups` | `GroupEntity` | `GroupDao` | id, name, description, avatarUrl, createdBy |
| `group_members` | `GroupMemberEntity` | `GroupDao` | groupId, userId, role, joinedAt, addedBy |

### 5.3 Supabase Backend Tables (Expected)

| Table | Purpose |
|-------|---------|
| `profiles` | User profiles (username, full_name, avatar_url, bio, phone, is_online, last_seen) |
| `conversations` | Chat rooms (is_group, last_message, updated_at) |
| `conversation_participants` | Many-to-many: users ↔ conversations |
| `messages` | All chat messages (content, message_type, media_url, reply_to_message_id, etc.) |
| `contacts` | User contact list (user_id, contact_user_id, nickname) |
| `calls` | Call history (caller_id, callee_id, call_type, status, room_url, tokens) |
| `stories` | Status updates (media_url, media_type, caption, expires_at) |
| `groups` | Group metadata (name, description, avatar_url, created_by) |
| `group_members` | Group membership (group_id, user_id, role) |
| `message_reactions` | Emoji reactions (message_id, user_id, emoji) |
| `starred_messages` | Starred messages per user |
| `archived_conversations` | Archived conversations per user |
| `pinned_conversations` | Pinned conversations per user |
| `muted_conversations` | Muted conversations per user |
| `blocked_users` | Block list per user |
| `user_devices` | FCM tokens per device |
| `user_sessions` | Active login sessions |
| `privacy_settings` | Per-user privacy configuration |
| `security_settings` | 2FA, biometric, etc. |

### 5.4 Supabase Edge Functions (Expected)

| Function | Purpose |
|----------|---------|
| `create-daily-room` | Creates a Daily.co room for a call |
| `manage-group-member` | Add/remove/promote group members |
| `send-notification` | Send FCM push notification to a user |
| `upload-fcm-token` | Store/update FCM token for a device |

---

## 6. Feature Status Matrix

Use this matrix to track what phase each feature is in:

| # | Feature | UI | ViewModel | Repository | Backend | E2E Tested |
|---|---------|----|-----------|-----------:|---------|------------|
| 1 | Email Auth | ✅ | ✅ | ✅ | ✅ | ✅ |
| 2 | 1-on-1 Chat | ✅ | ✅ | ✅ | ✅ | ✅ |
| 3 | Real-time Messages | ✅ | ✅ | ✅ | ✅ | ✅ |
| 4 | Audio Calling | ✅ | ✅ | ✅ | ✅ | ✅ |
| 5 | Video Calling | ✅ | ✅ | ✅ | ✅ | ✅ |
| 6 | Incoming Calls | ✅ | ✅ | ✅ | ✅ | ✅ |
| 7 | Push Notifications | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 8 | Media Sharing | ✅ | ✅ | ✅ | ✅ | ✅ |
| 9 | Voice Messages | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 10 | Message Reactions | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 11 | Reply/Edit/Delete | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 12 | Contact Management | ✅ | ✅ | ✅ | ✅ | ✅ |
| 13 | Profile Editing | ✅ | ✅ | ✅ | ✅ | ✅ |
| 14 | Stories | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 15 | Presence (Online/Offline) | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 16 | Read Receipts | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 17 | Typing Indicators | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| 18 | Pin/Archive/Mute | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 19 | Message Search | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 20 | Privacy Settings | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 21 | Security / 2FA | ✅ | ✅ | ✅ | 🟡 | 🔴 |
| 22 | Active Sessions | ✅ | ✅ | ✅ | 🟡 | 🔴 |
| 23 | Blocked Users | ✅ | ✅ | ✅ | ✅ | 🟡 |
| 24 | Group Chat Creation | ✅ | ✅ | ✅ | 🔴 | 🔴 |
| 25 | Group Messaging | 🟡 | 🟡 | 🟡 | 🔴 | 🔴 |
| 26 | Polls | ✅ | 🟡 | 🔴 | 🔴 | 🔴 |
| 27 | Vanish Mode | ✅ | 🟡 | 🔴 | 🔴 | 🔴 |
| 28 | Message Forwarding | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 |
| 29 | Media Gallery | 🟡 | 🔴 | 🔴 | 🔴 | 🔴 |
| 30 | QR Code Scanning | 🟡 | 🔴 | 🔴 | 🔴 | 🔴 |
| 31 | Notifications Screen | 🟡 | 🔴 | 🔴 | 🔴 | 🔴 |

Legend: ✅ Done | 🟡 Partial | 🔴 Not Started

---

## 7. Future Implementation Roadmap

### Phase 1: Group Chat Completion (Priority: 🔴 Critical)
**Goal**: Make group chats fully functional end-to-end.

| Task | Details | Effort |
|------|---------|--------|
| Create `groups` table in Supabase | Schema: id, name, description, avatar_url, created_by, created_at | S |
| Create `group_members` table | Schema: group_id, user_id, role (owner/admin/member), joined_at | S |
| Deploy `manage-group-member` Edge Function | Add/remove/promote members with RLS policies | M |
| Link group to conversation | Create a conversation with `is_group = true` when group is created | M |
| Group messaging flow | Ensure messages in group conversations show sender names and avatars | M |
| Group info screen | View/edit group name, description, members, avatar | L |
| Admin controls | Assign admins, restrict messaging, manage join permissions | L |
| Group notifications | Mention @user, mute group, notification settings | M |

### Phase 2: Polls & Interactive Messages (Priority: 🟡 High)
**Goal**: Make polls functional and add interactive message types.

| Task | Details | Effort |
|------|---------|--------|
| Create `polls` + `poll_votes` tables in Supabase | Schema for questions, options, votes | M |
| Connect `PollComposerBottomSheet` to backend | Wire the poll creation to Supabase | M |
| Connect `PollBubble` to live data | Fetch real votes, show percentages | M |
| Add "📍 Location" message type | Share Google Maps pin | L |
| Add "📇 Contact Card" message type | Share a contact profile | M |

### Phase 3: Message Forwarding & Starring (Priority: 🟡 High)
**Goal**: Complete message management features.

| Task | Details | Effort |
|------|---------|--------|
| Forward dialog with conversation picker | Allow selecting one or multiple conversations | L |
| Persist starred messages to Supabase | Create `starred_messages` table if missing | S |
| Starred messages screen | View all starred messages in one place | M |
| Message scheduling | Queue messages for future delivery | L |

### Phase 4: Vanish Mode (Priority: 🟢 Medium)
**Goal**: Auto-delete messages after viewing.

| Task | Details | Effort |
|------|---------|--------|
| Backend: expiration trigger | Supabase cron job to delete expired messages | M |
| Client: TTL metadata on messages | Set `expires_at` based on vanish mode duration | S |
| Client: UI indicator for vanishing messages | Show visual indicator on vanish-mode messages | S |

### Phase 5: Media Gallery & Search (Priority: 🟢 Medium)
**Goal**: Allow browsing all shared media in a conversation.

| Task | Details | Effort |
|------|---------|--------|
| Fetch media messages from conversation | Query messages with `message_type IN (image, video, document)` | M |
| Grid view for images/videos | Compose LazyVerticalGrid with thumbnails | M |
| Document list view | List all shared documents with download option | S |
| Link preview | Auto-detect URLs and show OpenGraph previews | L |

### Phase 6: Contact Sync & QR Code (Priority: 🟢 Medium)
**Goal**: Import contacts from phone and share profile via QR.

| Task | Details | Effort |
|------|---------|--------|
| Phone book sync | Read device contacts, match with registered users | L |
| QR code generation | Encode user profile link as QR | M |
| QR code scanning | Use CameraX to scan QR and navigate to user profile | M |
| Deep linking | Handle `loopchat://profile/{userId}` intents | M |

### Phase 7: True E2E Encryption (Priority: 🔴 Critical)
**Goal**: Implement proper key exchange and per-conversation encryption.

| Task | Details | Effort |
|------|---------|--------|
| Generate RSA key pair per device | Store private key in Android Keystore | L |
| Key exchange protocol | Share public keys via Supabase, verify fingerprints | XL |
| Session key management | Rotate AES session keys per conversation | L |
| Multi-device support | Encrypt for multiple devices per user | XL |
| Backup key escrow | Allow users to back up encryption keys | L |

### Phase 8: Group Calls (Priority: 🟡 High)
**Goal**: Support multi-participant audio and video calls.

| Task | Details | Effort |
|------|---------|--------|
| Create multi-user Daily.co room | Update `create-daily-room` Edge Function | M |
| Group call UI | Grid layout for multiple video feeds | L |
| Call controls | Mute all, speaker view, screen share | L |
| Group call invitation | Notify all group members of incoming call | M |

### Phase 9: Notifications History (Priority: 🟢 Medium)
**Goal**: Show notification history in-app.

| Task | Details | Effort |
|------|---------|--------|
| Create `notifications` table in Supabase | type, title, body, user_id, read, created_at | S |
| Connect `NotificationsScreen` to backend | Fetch and display notification history | M |
| Mark as read/unread | Track read state per notification | S |
| Notification preferences | Per-conversation notification settings | M |

### Phase 10: Production Readiness (Priority: 🔴 Critical)
**Goal**: Prepare the app for release.

| Task | Details | Effort |
|------|---------|--------|
| ProGuard/R8 configuration | Enable minification + obfuscation | M |
| Crash reporting (Firebase Crashlytics) | Add dependency + initialize | S |
| Analytics (Firebase Analytics) | Track key user actions | M |
| Performance optimization | Lazy loading, pagination, image caching | L |
| Accessibility audit | ContentDescription, font scaling, contrast | M |
| Multi-language (i18n) | Extract strings to `strings.xml`, add translations | L |
| App signing & keystore | Generate release keystore | S |
| Play Store listing | Screenshots, description, feature graphic | M |
| Privacy policy & Terms of Service | Legal documents | M |
| CI/CD pipeline (GitHub Actions) | Auto-build, test, deploy to Play Console | L |

---

## 8. Backend (Supabase) Requirements

### 8.1 Row Level Security (RLS) Policies

All tables **must** have RLS enabled. Key policies:

```sql
-- Messages: Users can only read messages from conversations they participate in
CREATE POLICY "Users can read their conversation messages"
ON messages FOR SELECT USING (
  conversation_id IN (
    SELECT conversation_id FROM conversation_participants
    WHERE user_id = auth.uid()
  )
);

-- Messages: Users can only insert messages in their conversations  
CREATE POLICY "Users can send messages to their conversations"
ON messages FOR INSERT WITH CHECK (
  sender_id = auth.uid() AND
  conversation_id IN (
    SELECT conversation_id FROM conversation_participants
    WHERE user_id = auth.uid()
  )
);

-- Groups: Only members can read group data
CREATE POLICY "Group members can read group"
ON groups FOR SELECT USING (
  id IN (SELECT group_id FROM group_members WHERE user_id = auth.uid())
);
```

### 8.2 Supabase Storage Buckets

| Bucket | Purpose | Max Size | Types |
|--------|---------|----------|-------|
| `avatars` | Profile pictures | 5 MB | image/* |
| `chat-media` | Chat images, videos, documents | 50 MB | image/*, video/*, application/* |
| `stories` | Story/status media | 10 MB | image/*, video/* |
| `voice-messages` | Audio recordings | 10 MB | audio/* |
| `group-avatars` | Group profile pictures | 5 MB | image/* |

### 8.3 Realtime Subscriptions

| Channel | Events | Purpose |
|---------|--------|---------|
| `messages:conversation_id=eq.{id}` | INSERT, UPDATE, DELETE | Live chat messages |
| `calls:callee_id=eq.{uid}` | INSERT, UPDATE | Incoming call detection |
| `profiles` | UPDATE | Online/offline presence |
| `typing_indicators` | INSERT | Typing indicator broadcast |

---

## 9. Testing Strategy

### 9.1 Unit Tests

| Target | Framework | Location |
|--------|-----------|----------|
| ViewModels | JUnit + MockK + Turbine | `app/src/test/` |
| Repositories | JUnit + MockK | `app/src/test/` |
| CryptoManager | JUnit | `app/src/test/` |
| Data Models | JUnit + Truth | `app/src/test/` |

### 9.2 Integration Tests

| Target | Framework | Location |
|--------|-----------|----------|
| Room DAOs | JUnit + Robolectric | `app/src/test/` |
| Ktor API Calls | JUnit + MockK | `app/src/test/` |

### 9.3 UI Tests

| Target | Framework | Location |
|--------|-----------|----------|
| Compose Screens | Compose UI Test | `app/src/androidTest/` |
| Navigation Flows | Compose Navigation Testing | `app/src/androidTest/` |
| End-to-End Flows | Espresso + Compose | `app/src/androidTest/` |

### 9.4 Test Coverage Goals

| Phase | Target |
|-------|--------|
| MVP (Current) | 20% coverage (critical paths) |
| Beta Release | 50% coverage (ViewModels + Repositories) |
| Production Release | 70%+ coverage (all layers) |

---

## 10. Release & Distribution

### 10.1 Build Variants

| Variant | Supabase Instance | Logging | Minification |
|---------|-------------------|---------|--------------|
| `debug` | Development | Verbose | Off |
| `staging` | Staging | Info | Off |
| `release` | Production | Error only | On (R8) |

### 10.2 Version Strategy

```
versionCode = YYYYMMDD + buildNumber  (e.g., 2026032201)
versionName = "major.minor.patch"      (e.g., "1.0.0")
```

### 10.3 Release Checklist

- [ ] All critical features E2E tested
- [ ] ProGuard rules configured (Ktor, Room, kotlinx.serialization)
- [ ] Firebase Crashlytics initialized
- [ ] App signing with release keystore
- [ ] Play Store listing complete (screenshots, description)
- [ ] Privacy policy URL configured
- [ ] Deep link verification (assetlinks.json)
- [ ] Performance profiling (startup time < 2s)
- [ ] Battery optimization whitelisting for call service
- [ ] Notification channel configuration verified

---

## Appendix A: Design System — Electric Noir

| Token | Value | Usage |
|-------|-------|-------|
| `Background` | `#0D0D0D` | App background |
| `Surface` | `#141414` | Card backgrounds |
| `SurfaceContainer` | `#1A1A1A` | Bottom nav, elevated surfaces |
| `Primary` | `#FF4D6A` | Accent pink (buttons, FAB, links) |
| `PrimaryContainer` | `#FF4D6A` | Sent message bubble gradient start |
| `PrimaryFixedDim` | `#E0436A` | Sent message bubble gradient end |
| `Secondary` | `#7C5CFC` | Purple accent |
| `TextPrimary` | `#F5F5F5` | Main text |
| `TextSecondary` | `#A3A3A3` | Subtitle text |
| `TextMuted` | `#666666` | Timestamps, hints |
| `ErrorColor` | `#FF5252` | Errors, missed calls |
| `Warning` | `#FFB020` | Stars, warnings |
| `Success` | `#4CAF50` | Online indicator, delivered |

**Typography**: Manrope (Display/Headline) + Inter (Body/Label) via Google Fonts Compose provider.

---

## Appendix B: Key File Quick Reference

| When you need to... | Go to... |
|---------------------|----------|
| Add a new screen | `ui/screens/` + `ui/navigation/Navigation.kt` |
| Add a new API call | `data/SupabaseRepository.kt` |
| Add a new Room table | `data/local/entities/` + `data/local/dao/` + `LoopChatDatabase.kt` |
| Modify chat message UI | `ui/screens/EnhancedChatScreen.kt` |
| Modify chat business logic | `ui/viewmodels/EnhancedChatViewModel.kt` |
| Add a new message feature | `data/MessagingFeaturesRepository.kt` |
| Change colors/theme | `ui/theme/Color.kt` + `ui/theme/Theme.kt` |
| Handle push notifications | `services/LoopChatMessagingService.kt` |
| Handle call logic | `data/DailyCallManager.kt` + `services/CallService.kt` |
| Add privacy/security feature | `data/PrivacySecurityRepository.kt` |

---

*This blueprint was auto-generated on March 22, 2026 based on a comprehensive audit of the Loop Chat Android codebase.*
