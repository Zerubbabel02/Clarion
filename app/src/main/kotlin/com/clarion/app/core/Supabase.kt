package com.clarion.app.core

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

// The publishable (anon) key is designed to be embedded in client apps — every row it can
// touch is still gated by this project's Postgres row-level security policies.
private const val SUPABASE_URL = "https://pgklzmisicdxfqikywoi.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_H9hZihVVhLlVgg_WOPCKoQ_2NkTwwqi"

val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_PUBLISHABLE_KEY,
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
    install(Storage)
}
