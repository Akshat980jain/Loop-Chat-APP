import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { z } from "https://deno.land/x/zod@v3.22.4/mod.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Input validation schema
const settingsSchema = z.object({
  show_typing_indicator: z.boolean().optional(),
  show_read_receipts: z.boolean().optional(),
  show_online_status: z.boolean().optional(),
  profile_photo_visibility: z.enum(['everyone', 'contacts', 'nobody']).optional(),
  status_visibility: z.enum(['everyone', 'contacts', 'nobody']).optional(),
  group_add_permission: z.enum(['everyone', 'contacts', 'nobody']).optional(),
  direct_message_notifications: z.boolean().optional(),
  group_message_notifications: z.boolean().optional(),
  mention_notifications: z.boolean().optional(),
  message_sounds: z.boolean().optional(),
  notification_sounds: z.boolean().optional(),
  do_not_disturb: z.boolean().optional(),
  two_factor_enabled: z.boolean().optional(),
  end_to_end_encryption: z.boolean().optional(),
  language: z.string().max(10).optional(),
  date_format: z.string().max(20).optional(),
  time_format: z.enum(['12-hour', '24-hour']).optional(),
  use_generated_avatar: z.boolean().optional(),
});

Deno.serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      {
        global: {
          headers: { Authorization: req.headers.get('Authorization')! },
        },
      }
    );

    // Get the user from the auth header
    const { data: { user }, error: userError } = await supabaseClient.auth.getUser();
    
    if (userError || !user) {
      console.error('Auth error:', userError);
      return new Response(
        JSON.stringify({ error: 'Unauthorized' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Parse and validate input
    const rawInput = await req.json();
    const parseResult = settingsSchema.safeParse(rawInput);
    
    if (!parseResult.success) {
      console.error('Validation error:', parseResult.error.errors);
      return new Response(
        JSON.stringify({ error: 'Invalid input', details: parseResult.error.errors.map(e => e.message) }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const settings = parseResult.data;

    console.log('Updating settings for user:', user.id, settings);

    // Update settings
    const { data: updatedSettings, error: updateError } = await supabaseClient
      .from('user_settings')
      .update(settings)
      .eq('user_id', user.id)
      .select()
      .single();

    if (updateError) {
      console.error('Update error:', updateError);
      
      // If settings don't exist, create them
      if (updateError.code === 'PGRST116') {
        const { data: newSettings, error: insertError } = await supabaseClient
          .from('user_settings')
          .insert({ user_id: user.id, ...settings })
          .select()
          .single();

        if (insertError) {
          return new Response(
            JSON.stringify({ error: insertError.message }),
            { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          );
        }

        console.log('Settings created successfully:', newSettings);
        return new Response(
          JSON.stringify({ settings: newSettings }),
          { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      return new Response(
        JSON.stringify({ error: updateError.message }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log('Settings updated successfully:', updatedSettings);

    return new Response(
      JSON.stringify({ settings: updatedSettings }),
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
