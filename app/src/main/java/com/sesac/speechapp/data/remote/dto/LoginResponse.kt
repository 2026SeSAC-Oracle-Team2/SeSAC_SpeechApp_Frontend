package com.sesac.speechapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("expires_in") val expiresIn: Int = 0,
    val user: UserDto? = null,
    @SerializedName("is_new_user") val isNewUser: Boolean = false,
)

data class UserDto(
    val uuid: String = "",
    val email: String = "",
    val nickname: String? = null,
    @SerializedName("profile_image_url") val profileImageUrl: String? = null,
    val level: Int? = null,
)
