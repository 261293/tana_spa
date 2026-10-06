package com.tana.spa.auth

import com.tana.spa.shared.auth.AuthValidation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AuthResult {
    data class Success(val user: User) : AuthResult
    data object InvalidCredentials : AuthResult
    data object EmailTaken : AuthResult
    data object PhoneTaken : AuthResult
    data object InvalidInput : AuthResult
}

/**
 * Password hashing (hundreds of ms of CPU) and JDBC are both blocking, so every operation runs on
 * [io] instead of tying up a Netty event-loop thread.
 */
class AuthService(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Region assumed for numbers typed without `+countrycode`. */
    private val defaultPhoneRegion: String = "VN",
) {
    // Verified when the email is unknown so that both failure paths cost the same time;
    // otherwise response latency would reveal which emails are registered.
    private val dummyHash = hasher.hash("timing-equaliser")

    suspend fun register(displayName: String, email: String, phone: String, password: String): AuthResult = withContext(io) {
        val name = displayName.trim()
        if (AuthValidation.validateName(name) != null) return@withContext AuthResult.InvalidInput
        if (AuthValidation.validateEmail(email) != null) return@withContext AuthResult.InvalidInput
        // libphonenumber, not the shared regex: this is the authoritative check for every country.
        val normalizedPhone = PhoneNumbers.parse(phone, defaultPhoneRegion)?.e164
            ?: return@withContext AuthResult.InvalidInput
        if (AuthValidation.validatePassword(password) != null ||
            password.length > AuthValidation.MAX_PASSWORD_LENGTH
        ) {
            return@withContext AuthResult.InvalidInput
        }

        when (
            val created = users.create(
                email = AuthValidation.normalizeEmail(email),
                displayName = name,
                phone = normalizedPhone,
                passwordHash = hasher.hash(password),
            )
        ) {
            is CreateUserResult.Created -> AuthResult.Success(created.user)
            CreateUserResult.EmailTaken -> AuthResult.EmailTaken
            CreateUserResult.PhoneTaken -> AuthResult.PhoneTaken
        }
    }

    suspend fun login(email: String, password: String): AuthResult = withContext(io) {
        // Cap length before hashing so a huge payload cannot be used to burn CPU.
        if (AuthValidation.validateEmail(email) != null || password.isEmpty() ||
            password.length > AuthValidation.MAX_PASSWORD_LENGTH
        ) {
            return@withContext AuthResult.InvalidCredentials
        }
        val user = users.findByEmail(AuthValidation.normalizeEmail(email))
        val ok = hasher.verify(password, user?.passwordHash ?: dummyHash)
        if (user != null && ok) {
            users.recordLogin(user.id)
            AuthResult.Success(user)
        } else {
            AuthResult.InvalidCredentials
        }
    }

    suspend fun findUser(id: String): User? = withContext(io) { users.findById(id) }
}
