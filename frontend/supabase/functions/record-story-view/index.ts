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

    const authHeader = req.headers.get('Authorization') ?? '';
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 401,
      });
    }
    
    const supabase = createClient(supabaseUrl, supabaseAnonKey, {
      global: {
        headers: { Authorization: authHeader },
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

    // Check if story exists and is not owned by the viewer (don't count own views)
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

    // Don't record if viewing own story
    if (story.user_id === user.id) {
      return new Response(JSON.stringify({ success: true, message: 'Own story view not recorded' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      });
    }

    // Check if already viewed using maybeSingle to avoid error when no row found
    const { data: existingView } = await supabase
      .from('story_views')
      .select('id')
      .eq('story_id', story_id)
      .eq('viewer_id', user.id)
      .maybeSingle();

    if (existingView) {
      console.log('View already recorded for user:', user.id, 'story:', story_id);
      return new Response(JSON.stringify({ success: true, message: 'Already viewed' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      });
    }

    // Record the view
    const { error: insertError } = await supabase
      .from('story_views')
      .insert({
        story_id,
        viewer_id: user.id
      });

    if (insertError) {
      console.error('Error recording view:', insertError);
      throw insertError;
    }

    return new Response(JSON.stringify({ success: true }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    });
  } catch (error: unknown) {
    console.error('Error in record-story-view:', error);
    const message = error instanceof Error ? error.message : 'Unknown error';
    return new Response(JSON.stringify({ error: message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 500,
    });
  }
});
