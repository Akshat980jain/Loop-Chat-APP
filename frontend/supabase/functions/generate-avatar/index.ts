import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { z } from "https://deno.land/x/zod@v3.22.4/mod.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Input validation schema
const avatarSchema = z.object({
  style: z.enum(['modern', 'cartoon', 'abstract', 'nature', 'pixel']).default('modern'),
});

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Missing authorization header' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      { global: { headers: { Authorization: authHeader } } }
    );

    const { data: { user }, error: userError } = await supabaseClient.auth.getUser();
    if (userError || !user) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // Parse and validate input
    const rawInput = await req.json();
    const parseResult = avatarSchema.safeParse(rawInput);
    
    if (!parseResult.success) {
      console.error('Validation error:', parseResult.error.errors);
      return new Response(
        JSON.stringify({ error: 'Invalid input', details: parseResult.error.errors.map(e => e.message) }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const { style } = parseResult.data;

    console.log(`Generating avatar for user ${user.id} with style: ${style}`);

    // Get user profile for personalization
    const { data: profile } = await supabaseClient
      .from('profiles')
      .select('full_name, username')
      .eq('user_id', user.id)
      .single();

    const name = profile?.full_name || profile?.username || 'User';
    const initial = name.charAt(0).toUpperCase();

    // Build prompt based on style
    let prompt = '';
    switch (style) {
      case 'cartoon':
        prompt = `A cute cartoon avatar character, friendly round face, big expressive eyes, soft pastel colors, minimal design, perfect for profile picture, clean white background, digital art style`;
        break;
      case 'abstract':
        prompt = `An abstract geometric avatar design, colorful overlapping shapes, modern minimalist art, gradient colors purple blue and teal, suitable for profile picture, clean composition`;
        break;
      case 'nature':
        prompt = `A nature-inspired avatar design, beautiful landscape silhouette, mountains and forest, sunset colors orange and purple, circular frame, minimalist style`;
        break;
      case 'pixel':
        prompt = `A pixel art style avatar, retro 8-bit character design, vibrant colors, friendly expression, clean edges, suitable for profile picture`;
        break;
      default: // modern
        prompt = `A modern stylized avatar portrait, clean geometric design, professional look, gradient background with soft purple and blue tones, minimalist aesthetic, suitable for Loop profile`;
    }

    const LOVABLE_API_KEY = Deno.env.get('LOVABLE_API_KEY');
    if (!LOVABLE_API_KEY) {
      throw new Error('LOVABLE_API_KEY is not configured');
    }

    // Use Lovable AI to generate avatar image
    const response = await fetch('https://ai.gateway.lovable.dev/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${LOVABLE_API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'google/gemini-2.5-flash-image-preview',
        messages: [
          {
            role: 'user',
            content: prompt
          }
        ],
        modalities: ['image', 'text']
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error('AI gateway error:', response.status, errorText);
      
      if (response.status === 429) {
        return new Response(JSON.stringify({ error: 'Rate limit exceeded. Please try again later.' }), {
          status: 429,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }
      if (response.status === 402) {
        return new Response(JSON.stringify({ error: 'AI credits exhausted. Please add credits.' }), {
          status: 402,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }
      throw new Error('Failed to generate avatar');
    }

    const data = await response.json();
    const imageUrl = data.choices?.[0]?.message?.images?.[0]?.image_url?.url;

    if (!imageUrl) {
      throw new Error('No image generated');
    }

    console.log('Avatar generated successfully');

    // Decode base64 and upload to storage
    const base64Data = imageUrl.replace(/^data:image\/\w+;base64,/, '');
    const imageBuffer = Uint8Array.from(atob(base64Data), c => c.charCodeAt(0));
    
    const timestamp = Date.now();
    const fileName = `generated-avatars/${user.id}/avatar_${timestamp}.png`;

    // Delete old generated avatars
    const { data: existingFiles } = await supabaseClient.storage
      .from('chat-attachments')
      .list(`generated-avatars/${user.id}`);
    
    if (existingFiles && existingFiles.length > 0) {
      const filesToDelete = existingFiles.map(f => `generated-avatars/${user.id}/${f.name}`);
      await supabaseClient.storage
        .from('chat-attachments')
        .remove(filesToDelete);
    }

    const { data: uploadData, error: uploadError } = await supabaseClient.storage
      .from('chat-attachments')
      .upload(fileName, imageBuffer, {
        contentType: 'image/png',
        upsert: true
      });

    if (uploadError) {
      console.error('Upload error:', uploadError);
      throw new Error('Failed to save avatar');
    }

    const { data: urlData } = supabaseClient.storage
      .from('chat-attachments')
      .getPublicUrl(fileName);

    // Update profile with generated avatar URL
    const { error: updateError } = await supabaseClient
      .from('profiles')
      .update({ generated_avatar_url: urlData.publicUrl })
      .eq('user_id', user.id);

    if (updateError) {
      console.error('Profile update error:', updateError);
    }

    console.log('Avatar saved and profile updated');

    return new Response(
      JSON.stringify({ 
        success: true, 
        avatar_url: urlData.publicUrl 
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error: unknown) {
    console.error('Error generating avatar:', error);
    const errorMessage = error instanceof Error ? error.message : 'Failed to generate avatar';
    return new Response(
      JSON.stringify({ error: errorMessage }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
