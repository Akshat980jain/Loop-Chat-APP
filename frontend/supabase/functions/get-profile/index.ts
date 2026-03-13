import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get('Authorization') ?? '';
    if (!authHeader) {
      return new Response(
        JSON.stringify({ error: 'Unauthorized' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      {
        global: {
          headers: { Authorization: authHeader },
        },
      }
    );

    const { data: { user }, error: userError } = await supabaseClient.auth.getUser();
    
    if (userError || !user) {
      console.error('Auth error:', userError);
      return new Response(
        JSON.stringify({ error: 'Unauthorized' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log('Fetching profile for user:', user.id);

    // Get profile - use maybeSingle() to avoid error when no rows
    let { data: profile, error: profileError } = await supabaseClient
      .from('profiles')
      .select('*')
      .eq('user_id', user.id)
      .maybeSingle();

    if (profileError) {
      console.error('Profile fetch error:', profileError);
      return new Response(
        JSON.stringify({ error: profileError.message }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // If no profile exists, create one
    if (!profile) {
      console.log('No profile found, creating one for user:', user.id);
      const metadata = user.user_metadata || {};
      
      const { data: newProfile, error: createError } = await supabaseClient
        .from('profiles')
        .insert({
          user_id: user.id,
          full_name: metadata.full_name || 'User',
          username: metadata.username || `user_${user.id.substring(0, 8)}`,
          phone: metadata.phone || null,
        })
        .select()
        .maybeSingle();

      if (createError) {
        console.error('Profile create error:', createError);
        return new Response(
          JSON.stringify({ error: createError.message }),
          { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }
      
      profile = newProfile;
      console.log('Profile created:', profile);
    }

    // Get settings - use maybeSingle() to avoid error when no rows
    let { data: settings, error: settingsError } = await supabaseClient
      .from('user_settings')
      .select('*')
      .eq('user_id', user.id)
      .maybeSingle();

    if (settingsError) {
      console.error('Settings fetch error:', settingsError);
    }

    // If no settings exist, create default
    if (!settings) {
      console.log('No settings found, creating defaults for user:', user.id);
      const { data: newSettings, error: createSettingsError } = await supabaseClient
        .from('user_settings')
        .insert({ user_id: user.id })
        .select()
        .maybeSingle();
      
      if (!createSettingsError) {
        settings = newSettings;
      }
    }

    // Get statistics
    console.log('Fetching statistics for user:', user.id);
    
    // Count messages sent by this user
    const { count: messageCount, error: msgCountError } = await supabaseClient
      .from('messages')
      .select('*', { count: 'exact', head: true })
      .eq('sender_id', user.id);

    if (msgCountError) {
      console.error('Message count error:', msgCountError);
    }

    // Count conversations the user is part of
    const { count: conversationCount, error: convCountError } = await supabaseClient
      .from('conversation_participants')
      .select('*', { count: 'exact', head: true })
      .eq('user_id', user.id);

    if (convCountError) {
      console.error('Conversation count error:', convCountError);
    }

    // Count calls made by this user
    const { count: callCount, error: callCountError } = await supabaseClient
      .from('call_history')
      .select('*', { count: 'exact', head: true })
      .eq('user_id', user.id);

    if (callCountError) {
      console.error('Call count error:', callCountError);
    }

    const statistics = {
      messages_sent: messageCount || 0,
      conversations: conversationCount || 0,
      calls_made: callCount || 0,
    };

    console.log('Profile, settings, and statistics fetched successfully');

    return new Response(
      JSON.stringify({ profile, settings, email: user.email, statistics }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Unexpected error:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});