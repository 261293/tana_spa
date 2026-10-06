package com.tana.spa.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class TokenService(private val config: AuthConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun issue(user: User, nowMillis: Long = System.currentTimeMillis()): String = JWT.create()
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .withSubject(user.id)
        .withIssuedAt(Date(nowMillis))
        .withExpiresAt(Date(nowMillis + config.tokenTtlSeconds * 1000))
        .sign(algorithm)
}
