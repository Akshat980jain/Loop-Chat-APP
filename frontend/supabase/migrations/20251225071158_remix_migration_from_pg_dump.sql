CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";
CREATE EXTENSION IF NOT EXISTS "plpgsql" WITH SCHEMA "pg_catalog";
CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";
BEGIN;

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.6
-- Dumped by pg_dump version 18.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: cleanup_expired_otps(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.cleanup_expired_otps() RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  DELETE FROM public.password_reset_otps WHERE expires_at < now();
END;
$$;


--
-- Name: handle_new_user(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.handle_new_user() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  INSERT INTO public.profiles (user_id, full_name, username, phone)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', 'User'),
    COALESCE(NEW.raw_user_meta_data->>'username', 'user_' || substring(NEW.id::text, 1, 8)),
    NEW.raw_user_meta_data->>'phone'
  );
  
  INSERT INTO public.user_settings (user_id)
  VALUES (NEW.id);
  
  RETURN NEW;
END;
$$;


--
-- Name: handle_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.handle_updated_at() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;


--
-- Name: is_conversation_participant(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_conversation_participant(conversation_uuid uuid, user_uuid uuid) RETURNS boolean
    LANGUAGE sql STABLE
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1
    FROM conversation_participants
    WHERE conversation_id = conversation_uuid
      AND user_id = user_uuid
  );
$$;


--
-- Name: user_is_participant_in_conversation(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.user_is_participant_in_conversation(conv_id uuid, uid uuid) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1
    FROM conversation_participants
    WHERE conversation_id = conv_id
      AND user_id = uid
  );
$$;


SET default_table_access_method = heap;

--
-- Name: call_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.call_history (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    other_participant_name text NOT NULL,
    other_participant_avatar text,
    call_type text NOT NULL,
    duration integer DEFAULT 0,
    status text NOT NULL,
    direction text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT call_history_call_type_check CHECK ((call_type = ANY (ARRAY['audio'::text, 'video'::text]))),
    CONSTRAINT call_history_direction_check CHECK ((direction = ANY (ARRAY['incoming'::text, 'outgoing'::text]))),
    CONSTRAINT call_history_status_check CHECK ((status = ANY (ARRAY['completed'::text, 'missed'::text, 'rejected'::text, 'ongoing'::text])))
);


--
-- Name: call_signals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.call_signals (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    call_id uuid NOT NULL,
    sender_id uuid NOT NULL,
    signal_type text NOT NULL,
    signal_data jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: calls; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.calls (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    caller_id uuid NOT NULL,
    callee_id uuid NOT NULL,
    call_type text DEFAULT 'audio'::text NOT NULL,
    status text DEFAULT 'ringing'::text NOT NULL,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    room_url text
);


--
-- Name: contacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contacts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    contact_user_id uuid NOT NULL,
    nickname text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: conversation_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversation_participants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    conversation_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: conversations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.conversations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    conversation_id uuid NOT NULL,
    sender_id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.messages REPLICA IDENTITY FULL;


--
-- Name: password_reset_otps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_otps (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    phone text NOT NULL,
    otp_code text NOT NULL,
    expires_at timestamp with time zone DEFAULT (now() + '00:10:00'::interval) NOT NULL,
    verified boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profiles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    full_name text NOT NULL,
    username text NOT NULL,
    phone text,
    status text,
    avatar_url text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    bio text,
    generated_avatar_url text
);


--
-- Name: stories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stories (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    media_url text NOT NULL,
    media_type text DEFAULT 'image'::text NOT NULL,
    caption text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone DEFAULT (now() + '24:00:00'::interval) NOT NULL
);


--
-- Name: story_views; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.story_views (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    story_id uuid NOT NULL,
    viewer_id uuid NOT NULL,
    viewed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_settings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    show_online_status boolean DEFAULT true,
    show_read_receipts boolean DEFAULT true,
    show_typing_indicator boolean DEFAULT true,
    profile_photo_visibility text DEFAULT 'everyone'::text,
    status_visibility text DEFAULT 'everyone'::text,
    group_add_permission text DEFAULT 'everyone'::text,
    direct_message_notifications boolean DEFAULT true,
    group_message_notifications boolean DEFAULT true,
    mention_notifications boolean DEFAULT true,
    message_sounds boolean DEFAULT true,
    notification_sounds boolean DEFAULT true,
    do_not_disturb boolean DEFAULT false,
    two_factor_enabled boolean DEFAULT false,
    end_to_end_encryption boolean DEFAULT true,
    language text DEFAULT 'en'::text,
    date_format text DEFAULT 'MM/DD/YYYY'::text,
    time_format text DEFAULT '12-hour'::text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    use_generated_avatar boolean DEFAULT false,
    generated_avatar_url text,
    CONSTRAINT user_settings_group_add_permission_check CHECK ((group_add_permission = ANY (ARRAY['everyone'::text, 'contacts'::text, 'nobody'::text]))),
    CONSTRAINT user_settings_profile_photo_visibility_check CHECK ((profile_photo_visibility = ANY (ARRAY['everyone'::text, 'contacts'::text, 'nobody'::text]))),
    CONSTRAINT user_settings_status_visibility_check CHECK ((status_visibility = ANY (ARRAY['everyone'::text, 'contacts'::text, 'nobody'::text])))
);


--
-- Name: call_history call_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.call_history
    ADD CONSTRAINT call_history_pkey PRIMARY KEY (id);


--
-- Name: call_signals call_signals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.call_signals
    ADD CONSTRAINT call_signals_pkey PRIMARY KEY (id);


--
-- Name: calls calls_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.calls
    ADD CONSTRAINT calls_pkey PRIMARY KEY (id);


--
-- Name: contacts contacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contacts
    ADD CONSTRAINT contacts_pkey PRIMARY KEY (id);


--
-- Name: contacts contacts_user_id_contact_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contacts
    ADD CONSTRAINT contacts_user_id_contact_user_id_key UNIQUE (user_id, contact_user_id);


--
-- Name: conversation_participants conversation_participants_conversation_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants
    ADD CONSTRAINT conversation_participants_conversation_id_user_id_key UNIQUE (conversation_id, user_id);


--
-- Name: conversation_participants conversation_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants
    ADD CONSTRAINT conversation_participants_pkey PRIMARY KEY (id);


--
-- Name: conversations conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_pkey PRIMARY KEY (id);


--
-- Name: messages messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_pkey PRIMARY KEY (id);


--
-- Name: password_reset_otps password_reset_otps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_otps
    ADD CONSTRAINT password_reset_otps_pkey PRIMARY KEY (id);


--
-- Name: profiles profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);


--
-- Name: profiles profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_user_id_key UNIQUE (user_id);


--
-- Name: profiles profiles_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_username_key UNIQUE (username);


--
-- Name: stories stories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stories
    ADD CONSTRAINT stories_pkey PRIMARY KEY (id);


--
-- Name: story_views story_views_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.story_views
    ADD CONSTRAINT story_views_pkey PRIMARY KEY (id);


--
-- Name: user_settings user_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_settings
    ADD CONSTRAINT user_settings_pkey PRIMARY KEY (id);


--
-- Name: user_settings user_settings_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_settings
    ADD CONSTRAINT user_settings_user_id_key UNIQUE (user_id);


--
-- Name: idx_call_history_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_call_history_created_at ON public.call_history USING btree (created_at DESC);


--
-- Name: idx_call_history_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_call_history_user_id ON public.call_history USING btree (user_id);


--
-- Name: idx_password_reset_otps_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_otps_expires ON public.password_reset_otps USING btree (expires_at);


--
-- Name: idx_password_reset_otps_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_otps_phone ON public.password_reset_otps USING btree (phone);


--
-- Name: idx_stories_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stories_expires_at ON public.stories USING btree (expires_at);


--
-- Name: idx_stories_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stories_user_id ON public.stories USING btree (user_id);


--
-- Name: idx_story_views_story_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_story_views_story_id ON public.story_views USING btree (story_id);


--
-- Name: idx_story_views_viewer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_story_views_viewer_id ON public.story_views USING btree (viewer_id);


--
-- Name: profiles set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();


--
-- Name: conversations update_conversations_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_conversations_updated_at BEFORE UPDATE ON public.conversations FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();


--
-- Name: user_settings update_user_settings_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_user_settings_updated_at BEFORE UPDATE ON public.user_settings FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();


--
-- Name: call_signals call_signals_call_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.call_signals
    ADD CONSTRAINT call_signals_call_id_fkey FOREIGN KEY (call_id) REFERENCES public.calls(id) ON DELETE CASCADE;


--
-- Name: conversation_participants conversation_participants_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants
    ADD CONSTRAINT conversation_participants_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.conversations(id) ON DELETE CASCADE;


--
-- Name: conversation_participants conversation_participants_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.conversation_participants
    ADD CONSTRAINT conversation_participants_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(user_id) ON DELETE CASCADE;


--
-- Name: messages messages_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.conversations(id) ON DELETE CASCADE;


--
-- Name: messages messages_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.profiles(user_id) ON DELETE CASCADE;


--
-- Name: profiles profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: story_views story_views_story_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.story_views
    ADD CONSTRAINT story_views_story_id_fkey FOREIGN KEY (story_id) REFERENCES public.stories(id) ON DELETE CASCADE;


--
-- Name: conversations Allow authenticated users to create conversations; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Allow authenticated users to create conversations" ON public.conversations FOR INSERT TO authenticated WITH CHECK (true);


--
-- Name: conversation_participants Allow authenticated users to insert participants; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Allow authenticated users to insert participants" ON public.conversation_participants FOR INSERT TO authenticated WITH CHECK (true);


--
-- Name: conversations Allow participants to view conversations; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Allow participants to view conversations" ON public.conversations FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM public.conversation_participants
  WHERE ((conversation_participants.conversation_id = conversations.id) AND (conversation_participants.user_id = auth.uid())))));


--
-- Name: conversation_participants Allow users to view participants in their conversations; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Allow users to view participants in their conversations" ON public.conversation_participants FOR SELECT TO authenticated USING (((user_id = auth.uid()) OR (EXISTS ( SELECT 1
   FROM public.conversation_participants cp
  WHERE ((cp.conversation_id = conversation_participants.conversation_id) AND (cp.user_id = auth.uid()))))));


--
-- Name: stories Anyone can view active stories; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Anyone can view active stories" ON public.stories FOR SELECT USING ((expires_at > now()));


--
-- Name: story_views Story owners can see views; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Story owners can see views" ON public.story_views FOR SELECT USING ((EXISTS ( SELECT 1
   FROM public.stories
  WHERE ((stories.id = story_views.story_id) AND ((stories.user_id)::text = (auth.uid())::text)))));


--
-- Name: contacts Users can add contacts; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can add contacts" ON public.contacts FOR INSERT WITH CHECK ((auth.uid() = user_id));


--
-- Name: calls Users can create calls; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can create calls" ON public.calls FOR INSERT WITH CHECK ((auth.uid() = caller_id));


--
-- Name: stories Users can create their own stories; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can create their own stories" ON public.stories FOR INSERT WITH CHECK (((auth.uid())::text = (user_id)::text));


--
-- Name: contacts Users can delete their contacts; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can delete their contacts" ON public.contacts FOR DELETE USING ((auth.uid() = user_id));


--
-- Name: stories Users can delete their own stories; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can delete their own stories" ON public.stories FOR DELETE USING (((auth.uid())::text = (user_id)::text));


--
-- Name: messages Users can insert messages in their conversations; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can insert messages in their conversations" ON public.messages FOR INSERT TO authenticated WITH CHECK (((sender_id = auth.uid()) AND (EXISTS ( SELECT 1
   FROM public.conversation_participants
  WHERE ((conversation_participants.conversation_id = messages.conversation_id) AND (conversation_participants.user_id = auth.uid()))))));


--
-- Name: call_signals Users can insert signals for their calls; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can insert signals for their calls" ON public.call_signals FOR INSERT WITH CHECK (((auth.uid() = sender_id) AND (EXISTS ( SELECT 1
   FROM public.calls
  WHERE ((calls.id = call_signals.call_id) AND ((calls.caller_id = auth.uid()) OR (calls.callee_id = auth.uid())))))));


--
-- Name: call_history Users can insert their own call history; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can insert their own call history" ON public.call_history FOR INSERT WITH CHECK ((auth.uid() = user_id));


--
-- Name: profiles Users can insert their own profile; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can insert their own profile" ON public.profiles FOR INSERT TO authenticated WITH CHECK ((auth.uid() = user_id));


--
-- Name: user_settings Users can insert their own settings; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can insert their own settings" ON public.user_settings FOR INSERT WITH CHECK ((auth.uid() = user_id));


--
-- Name: story_views Users can mark stories as viewed; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can mark stories as viewed" ON public.story_views FOR INSERT WITH CHECK (((auth.uid())::text = (viewer_id)::text));


--
-- Name: messages Users can read messages in their conversations; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can read messages in their conversations" ON public.messages FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM public.conversation_participants
  WHERE ((conversation_participants.conversation_id = messages.conversation_id) AND (conversation_participants.user_id = auth.uid())))));


--
-- Name: contacts Users can update their contacts; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can update their contacts" ON public.contacts FOR UPDATE USING ((auth.uid() = user_id));


--
-- Name: call_history Users can update their own call history; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can update their own call history" ON public.call_history FOR UPDATE USING ((auth.uid() = user_id));


--
-- Name: calls Users can update their own calls; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can update their own calls" ON public.calls FOR UPDATE USING (((auth.uid() = caller_id) OR (auth.uid() = callee_id)));


--
-- Name: profiles Users can update their own profile; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can update their own profile" ON public.profiles FOR UPDATE TO authenticated USING ((auth.uid() = user_id)) WITH CHECK ((auth.uid() = user_id));


--
-- Name: user_settings Users can update their own settings; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can update their own settings" ON public.user_settings FOR UPDATE USING ((auth.uid() = user_id)) WITH CHECK ((auth.uid() = user_id));


--
-- Name: profiles Users can view all profiles; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can view all profiles" ON public.profiles FOR SELECT TO authenticated USING (true);


--
-- Name: call_signals Users can view signals for their calls; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can view signals for their calls" ON public.call_signals FOR SELECT USING ((EXISTS ( SELECT 1
   FROM public.calls
  WHERE ((calls.id = call_signals.call_id) AND ((calls.caller_id = auth.uid()) OR (calls.callee_id = auth.uid()))))));


--
-- Name: call_history Users can view their own call history; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can view their own call history" ON public.call_history FOR SELECT USING ((auth.uid() = user_id));


--
-- Name: calls Users can view their own calls; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can view their own calls" ON public.calls FOR SELECT USING (((auth.uid() = caller_id) OR (auth.uid() = callee_id)));


--
-- Name: contacts Users can view their own contacts; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can view their own contacts" ON public.contacts FOR SELECT USING ((auth.uid() = user_id));


--
-- Name: user_settings Users can view their own settings; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "Users can view their own settings" ON public.user_settings FOR SELECT USING ((auth.uid() = user_id));


--
-- Name: call_history; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.call_history ENABLE ROW LEVEL SECURITY;

--
-- Name: call_signals; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.call_signals ENABLE ROW LEVEL SECURITY;

--
-- Name: calls; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;

--
-- Name: contacts; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.contacts ENABLE ROW LEVEL SECURITY;

--
-- Name: password_reset_otps; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.password_reset_otps ENABLE ROW LEVEL SECURITY;

--
-- Name: profiles; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

--
-- Name: stories; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;

--
-- Name: story_views; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.story_views ENABLE ROW LEVEL SECURITY;

--
-- Name: user_settings; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;

--
-- PostgreSQL database dump complete
--




COMMIT;