package com.tana.spa.shared

object SupabasePaths {
    fun rpc(name: String): String = "/rest/v1/rpc/$name"
    const val SIGN_UP = "/auth/v1/signup"
    const val PASSWORD_GRANT = "/auth/v1/token?grant_type=password"
    const val REFRESH_GRANT = "/auth/v1/token?grant_type=refresh_token"
    const val LOGOUT = "/auth/v1/logout"
    const val USER = "/auth/v1/user"
    const val VERIFY = "/auth/v1/verify"
    fun function(name: String): String = "/functions/v1/$name"
}
