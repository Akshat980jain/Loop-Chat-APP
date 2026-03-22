import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { corsHeaders } from "../_shared/cors.ts"

interface ContactPayload {
  contacts: string[]; // Phone numbers or emails
}

serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
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
    )

    // Verify token & get user
    const { data: { user }, error: authError } = await supabaseClient.auth.getUser()
    if (authError || !user) throw new Error('Unauthorized')

    const body: ContactPayload = await req.json()
    const contacts = body.contacts || []

    if (contacts.length === 0) {
      return new Response(JSON.stringify({ matched_profiles: [] }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      })
    }

    // 1. Sanitize the incoming contacts to remove non-digit characters for phone comparison
    const sanitizedPhones = contacts.map(c => c.replace(/\D/g,''))
      .filter(c => c.length > 5); // Basic check
    
    // We also want to support raw email / username matching just in case
    const rawIdentifiers = contacts.filter(c => c.includes('@') || c.length > 3);

    // 2. Query the profiles table where phone or username matches
    // Note: this query uses an 'or' filter. The phone column in DB should ideally be standardized
    let query = supabaseClient.from('profiles').select('id, user_id, username, full_name, avatar_url, phone, is_online, last_seen')
      
    if (sanitizedPhones.length > 0) {
      // In a real app we'd construct complex OR filters or use a postgres function. 
      // For now, simpler exact match:
      query = query.in('phone', sanitizedPhones)
    }

    const { data: matchedProfiles, error: fetchError } = await query

    if (fetchError) {
      throw fetchError
    }

    // Filter out the current user just in case
    const filteredMatches = matchedProfiles?.filter((p: any) => (p.user_id || p.id) !== user.id) || [];

    return new Response(JSON.stringify({ matched_profiles: filteredMatches }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })
  } catch (error: any) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
