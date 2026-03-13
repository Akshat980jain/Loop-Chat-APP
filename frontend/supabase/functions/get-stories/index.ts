import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

interface StoryRow {
  id: string;
  user_id: string;
  media_url: string;
  media_type: string;
  caption: string | null;
  created_at: string;
  expires_at: string;
}

interface ProfileRow {
  user_id: string;
  full_name: string;
  username: string;
  avatar_url: string | null;
  status: string | null;
}

interface ViewRow {
  story_id: string;
}

interface ContactRow {
  contact_user_id: string;
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY')!;

    // Check for auth header
    const authHeader = req.headers.get('Authorization') ?? '';
    let supabaseAuthed: ReturnType<typeof createClient> | null = null;
    let currentUserId: string | null = null;

    if (authHeader) {
      supabaseAuthed = createClient(supabaseUrl, supabaseAnonKey, {
        global: { headers: { Authorization: authHeader } },
      });

      const { data: { user }, error: userError } = await supabaseAuthed.auth.getUser();
      if (!userError && user) {
        currentUserId = user.id;
      } else {
        supabaseAuthed = null;
        currentUserId = null;
      }
    }

    // If not authenticated, return empty stories (only contacts can see each other's stories)
    if (!currentUserId || !supabaseAuthed) {
      console.log('No authenticated user, returning empty stories');
      return new Response(JSON.stringify({ stories: [] }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      });
    }

    console.log('Fetching stories for user:', currentUserId);

    // Get the user's contacts
    const { data: contactsData, error: contactsError } = await supabaseAuthed
      .from('contacts')
      .select('contact_user_id')
      .eq('user_id', currentUserId);

    if (contactsError) {
      console.error('Error fetching contacts:', contactsError);
    }

    const contacts = (contactsData ?? []) as ContactRow[];
    const contactUserIds = contacts.map((c: ContactRow) => c.contact_user_id);
    
    // Include current user's own ID to see their own stories
    const allowedUserIds = [...contactUserIds, currentUserId];
    
    console.log(`User has ${contactUserIds.length} contacts`);

    // Get active stories only from contacts and self
    const { data: storiesData, error: storiesError } = await supabaseAuthed
      .from('stories')
      .select('id,user_id,media_url,media_type,caption,created_at,expires_at')
      .in('user_id', allowedUserIds)
      .gt('expires_at', new Date().toISOString())
      .order('created_at', { ascending: false });

    if (storiesError) {
      console.error('Error fetching stories:', storiesError);
      throw storiesError;
    }

    const stories = (storiesData ?? []) as StoryRow[];
    console.log(`Found ${stories.length} active stories from contacts`);

    // Get unique user IDs from stories
    const userIds = [...new Set(stories.map((s: StoryRow) => s.user_id))];

    // Fetch profiles for those users (use authenticated client for proper RLS)
    let profiles: Record<string, ProfileRow> = {};
    if (userIds.length > 0) {
      const { data: profilesData, error: profilesError } = await supabaseAuthed
        .from('profiles')
        .select('user_id, full_name, username, avatar_url, status')
        .in('user_id', userIds);

      if (profilesError) {
        console.error('Error fetching profiles:', profilesError);
      } else {
        const typedProfiles = (profilesData ?? []) as ProfileRow[];
        profiles = typedProfiles.reduce((acc: Record<string, ProfileRow>, p: ProfileRow) => {
          acc[p.user_id] = p;
          return acc;
        }, {});
        console.log(`Fetched ${typedProfiles.length} profiles`);
      }
    }

    // Fetch view counts for current user's stories AND which stories the user has viewed
    let viewCounts: Record<string, number> = {};
    let viewedStoryIds: Set<string> = new Set();

    // Get view counts for user's own stories
    const userStoryIds = stories
      .filter((s: StoryRow) => s.user_id === currentUserId)
      .map((s: StoryRow) => s.id);

    if (userStoryIds.length > 0) {
      const { data: viewsData, error: viewsError } = await supabaseAuthed
        .from('story_views')
        .select('story_id')
        .in('story_id', userStoryIds);

      if (!viewsError && viewsData) {
        const typedViews = viewsData as ViewRow[];
        typedViews.forEach((v: ViewRow) => {
          viewCounts[v.story_id] = (viewCounts[v.story_id] || 0) + 1;
        });
      }
    }

    // Get stories this user has viewed (for other users' stories)
    const otherStoryIds = stories
      .filter((s: StoryRow) => s.user_id !== currentUserId)
      .map((s: StoryRow) => s.id);

    if (otherStoryIds.length > 0) {
      const { data: userViewsData, error: userViewsError } = await supabaseAuthed
        .from('story_views')
        .select('story_id')
        .eq('viewer_id', currentUserId)
        .in('story_id', otherStoryIds);

      if (!userViewsError && userViewsData) {
        const typedUserViews = userViewsData as ViewRow[];
        viewedStoryIds = new Set(typedUserViews.map((v: ViewRow) => v.story_id));
      }
    }

    // Group stories by user
    const storiesByUser: Record<string, any> = {};
    for (const story of stories) {
      if (!storiesByUser[story.user_id]) {
        const profile = profiles[story.user_id] || {
          user_id: story.user_id,
          full_name: 'Unknown User',
          username: 'unknown',
          avatar_url: null,
          status: 'offline'
        };

        storiesByUser[story.user_id] = {
          user_id: story.user_id,
          profile,
          stories: [],
          has_unviewed: false
        };
      }

      const isViewed = viewedStoryIds.has(story.id);

      // Mark user as having unviewed stories if any story is not viewed
      if (!isViewed && story.user_id !== currentUserId) {
        storiesByUser[story.user_id].has_unviewed = true;
      }

      storiesByUser[story.user_id].stories.push({
        id: story.id,
        media_url: story.media_url,
        media_type: story.media_type,
        caption: story.caption,
        created_at: story.created_at,
        expires_at: story.expires_at,
        view_count: viewCounts[story.id] || 0,
        is_viewed: isViewed
      });
    }

    const groupedStories = Object.values(storiesByUser);
    console.log(`Grouped into ${groupedStories.length} users with stories`);

    return new Response(JSON.stringify({ stories: groupedStories }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    });
  } catch (error: unknown) {
    console.error('Error in get-stories:', error);
    const message = error instanceof Error ? error.message : 'Unknown error';
    return new Response(JSON.stringify({ error: message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 500,
    });
  }
});
