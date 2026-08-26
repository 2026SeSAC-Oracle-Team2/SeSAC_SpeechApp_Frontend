package com.sesac.speechapp.data.model

import java.util.UUID

data class User(
    val uuid: String = UUID.randomUUID().toString(),
    val email: String = "",
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val level: Int = 1,
)
