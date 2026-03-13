import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY')!;
    
    const supabase = createClient(supabaseUrl, supabaseAnonKey, {
      global: {
        headers: { Authorization: req.headers.get('Authorization')! },
      },
    });

    // Get current user (optional - for view counts and viewed status)
    const { data: { user } } = await supabase.auth.getUser();
    const currentUserId = user?.id;

    console.log('Fetching active stories...');

    // Get all active (non-expired) stories with user profile info
    const { data: stories, error } = await supabase
      .from('stories')
      .select(`
        id,
        user_id,
        media_url,
        media_type,
        caption,
        created_at,
        expires_at
      `)
      .gt('expires_at', new Date().toISOString())
      .order('created_at', { ascending: false });

    if (error) {
      console.error('Error fetching stories:', error);
      throw error;
    }

    console.log(`Found ${stories?.length || 0} active stories`);

    // Get unique user IDs from stories
    const userIds = [...new Set(stories?.map(s => s.user_id) || [])];
    
    // Fetch profiles for those users
    let profiles: Record<string, any> = {};
    if (userIds.length > 0) {
      const { data: profilesData, error: profilesError } = await supabase
        .from('profiles')
        .select('user_id, full_name, username, avatar_url, status')
        .in('user_id', userIds);

      if (profilesError) {
        console.error('Error fetching profiles:', profilesError);
      } else {
        profiles = (profilesData || []).reduce((acc, p) => {
          acc[p.user_id] = p;
          return acc;
        }, {} as Record<string, any>);
      }
    }

    // Fetch view counts for current user's stories AND which stories the user has viewed
    let viewCounts: Record<string, number> = {};
    let viewedStoryIds: Set<string> = new Set();
    
    if (currentUserId) {
      // Get view counts for user's own stories
      const userStoryIds = stories?.filter(s => s.user_id === currentUserId).map(s => s.id) || [];
      if (userStoryIds.length > 0) {
        const { data: viewsData, error: viewsError } = await supabase
          .from('story_views')
          .select('story_id')
          .in('story_id', userStoryIds);

        if (!viewsError && viewsData) {
          viewCounts = viewsData.reduce((acc, v) => {
            acc[v.story_id] = (acc[v.story_id] || 0) + 1;
            return acc;
          }, {} as Record<string, number>);
        }
      }

      // Get stories this user has viewed (for other users' stories)
      const otherStoryIds = stories?.filter(s => s.user_id !== currentUserId).map(s => s.id) || [];
      if (otherStoryIds.length > 0) {
        const { data: userViewsData, error: userViewsError } = await supabase
          .from('story_views')
          .select('story_id')
          .eq('viewer_id', currentUserId)
          .in('story_id', otherStoryIds);

        if (!userViewsError && userViewsData) {
          viewedStoryIds = new Set(userViewsData.map(v => v.story_id));
        }
      }
    }

    // Group stories by user
    const storiesByUser: Record<string, any> = {};
    for (const story of stories || []) {
      if (!storiesByUser[story.user_id]) {
        const profile = profiles[story.user_id] || {
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
