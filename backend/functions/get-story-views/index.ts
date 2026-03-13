import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

serve(async (req) => {
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

    // Get current user
    const { data: { user }, error: userError } = await supabase.auth.getUser();
    if (userError || !user) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 401,
      });
    }

    const { story_id } = await req.json();

    if (!story_id) {
      return new Response(JSON.stringify({ error: 'story_id is required' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 400,
      });
    }

    // Verify user owns this story
    const { data: story, error: storyError } = await supabase
      .from('stories')
      .select('user_id')
      .eq('id', story_id)
      .single();

    if (storyError || !story) {
      return new Response(JSON.stringify({ error: 'Story not found' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 404,
      });
    }

    if (story.user_id !== user.id) {
      return new Response(JSON.stringify({ error: 'You can only view stats for your own stories' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 403,
      });
    }

    // Get views with viewer profiles
    const { data: views, error: viewsError } = await supabase
      .from('story_views')
      .select('id, viewer_id, viewed_at')
      .eq('story_id', story_id)
      .order('viewed_at', { ascending: false });

    if (viewsError) {
      console.error('Error fetching views:', viewsError);
      throw viewsError;
    }

    // Get viewer profiles
    const viewerIds = [...new Set(views?.map(v => v.viewer_id) || [])];
    let viewers: any[] = [];

    if (viewerIds.length > 0) {
      const { data: profilesData, error: profilesError } = await supabase
        .from('profiles')
        .select('user_id, full_name, username, avatar_url')
        .in('user_id', viewerIds);

      if (!profilesError && profilesData) {
        const profileMap = profilesData.reduce((acc, p) => {
          acc[p.user_id] = p;
          return acc;
        }, {} as Record<string, any>);

        viewers = (views || []).map(v => ({
          id: v.id,
          viewed_at: v.viewed_at,
          viewer: profileMap[v.viewer_id] || {
            user_id: v.viewer_id,
            full_name: 'Unknown User',
            username: 'unknown',
            avatar_url: null
          }
        }));
      }
    }

    return new Response(JSON.stringify({ 
      views: viewers,
      count: viewers.length 
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    });
  } catch (error: unknown) {
    console.error('Error in get-story-views:', error);
    const message = error instanceof Error ? error.message : 'Unknown error';
    return new Response(JSON.stringify({ error: message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 500,
    });
  }
});
