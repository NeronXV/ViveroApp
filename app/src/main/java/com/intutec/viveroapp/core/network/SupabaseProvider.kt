package com.intutec.viveroapp.core.network

import com.intutec.viveroapp.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Inject
import javax.inject.Singleton

interface ISupabaseProvider {
    val isConfigured: Boolean
    val client: SupabaseClient?
}

@Singleton
class SupabaseProvider @Inject constructor() : ISupabaseProvider {
    override val isConfigured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() &&
        BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    override val client: SupabaseClient? by lazy {
        if (!isConfigured) return@lazy null
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
