# Backend Directory

This folder contains **copies** of the backend edge functions for reference purposes.

## ⚠️ Important Note

The **actual working backend files** are located in `supabase/functions/`. This is required by the Supabase platform for automatic deployment.

**DO NOT modify files in this `backend/` folder expecting them to take effect.** Always edit the files in `supabase/functions/` instead.

## Structure

```
supabase/functions/          ← ACTUAL backend (deployed automatically)
├── create-story/
├── delete-account/
├── generate-avatar/
├── get-profile/
├── get-stories/
├── get-story-views/
├── record-story-view/
├── update-profile/
├── update-settings/
├── upload-avatar/
└── voice-to-text/

backend/                     ← REFERENCE COPIES (this folder)
├── README.md
└── functions/
    └── [copies of all edge functions]
```

## Edge Functions Overview

| Function | Description | Auth Required |
|----------|-------------|---------------|
| `create-story` | Create a new story with media | Yes |
| `delete-account` | Delete user account and all related data | Yes |
| `generate-avatar` | Generate AI-powered avatar images | Yes |
| `get-profile` | Fetch user profile, settings, and statistics | Yes |
| `get-stories` | Fetch all active stories grouped by user | No |
| `get-story-views` | Get view count and viewer details for a story | Yes |
| `record-story-view` | Record when a user views a story | Yes |
| `update-profile` | Update user profile information | Yes |
| `update-settings` | Update user preferences and settings | Yes |
| `upload-avatar` | Upload and update user avatar image | Yes |
| `voice-to-text` | Convert audio to text using OpenAI Whisper | Yes |

## API Endpoints

All functions are accessible at:
```
https://vqqtrulkpfvnaslhlvlj.supabase.co/functions/v1/{function-name}
```

## Authentication

Most functions require a valid JWT token in the `Authorization` header:
```
Authorization: Bearer <your-jwt-token>
```

Functions with `verify_jwt = false` in `supabase/config.toml` can be called without authentication.

## Syncing Changes

If you need to keep these reference copies in sync, copy files from `supabase/functions/` to `backend/functions/`.
