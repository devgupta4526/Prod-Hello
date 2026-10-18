package com.example.romear.feature.auth.domain

data class Account(val status: String, val hasDateOfBirth: Boolean, val hasProfile: Boolean, val hasPhoto: Boolean, val guidelinesAccepted: Boolean)
data class OtpChallenge(val id: String, val resendAtMillis: Long)
class AuthFailure(val code: String, val retryAfterSeconds: Int = 0) : Exception(code)
enum class OnboardingStep { SPLASH, WELCOME, PHONE, OTP, DATE_OF_BIRTH, UNDER_AGE, PROFILE, GUIDELINES, LOCATION, NOTIFICATIONS, AVAILABILITY, SUSPENDED, DELETION_PENDING, UPDATE, MAINTENANCE, COMPLETE }
