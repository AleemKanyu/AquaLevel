package com.aqualevel.app

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    suspend fun ensureSignedIn(): Result<Unit> = runCatching {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }.map { Unit }

    fun currentUserId(): String? = auth.currentUser?.uid
}
