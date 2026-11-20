package com.aperturescience.terminal.minitel

import com.aperturescience.terminal.Mode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable mirror of `logic`'s [Mode] (not `@Serializable` itself) - converts via
 * [toDomain]/[toData]; each case has an explicit [SerialName] to keep the wire format stable.
 */
@Serializable
sealed interface MinitelMode {
    @Serializable
    sealed interface Login : MinitelMode {
        @Serializable
        @SerialName("login_initial")
        data object Initial : Login

        @Serializable
        @SerialName("login_username")
        data object Username : Login

        @Serializable
        @SerialName("login_password")
        data class Password(
            val isRetry: Boolean,
        ) : Login

        @Serializable
        @SerialName("login_help")
        data object Help : Login

        @Serializable
        @SerialName("login_application_intro")
        data object ApplicationIntro : Login

        @Serializable
        @SerialName("login_application_uid_display")
        data object ApplicationUidDisplay : Login

        @Serializable
        @SerialName("login_uin_entry")
        data object UinEntry : Login

        @Serializable
        @SerialName("login_terminal")
        data object Terminal : Login
    }

    @Serializable
    @SerialName("shell")
    data class Shell(
        val message: String = "",
    ) : MinitelMode

    @Serializable
    @SerialName("application")
    data class Application(
        val questionNumber: Int,
        val pageOffset: Int = 0,
    ) : MinitelMode

    @Serializable
    @SerialName("notes")
    data class Notes(
        val page: Int,
    ) : MinitelMode

    @Serializable
    @SerialName("cake")
    data object Cake : MinitelMode

    @Serializable
    @SerialName("bosskey")
    data object BossKey : MinitelMode
}

fun Mode.toData(): MinitelMode =
    when (this) {
        Mode.Login.Initial -> MinitelMode.Login.Initial
        Mode.Login.Username -> MinitelMode.Login.Username
        is Mode.Login.Password -> MinitelMode.Login.Password(isRetry = isRetry)
        Mode.Login.Help -> MinitelMode.Login.Help
        Mode.Login.ApplicationIntro -> MinitelMode.Login.ApplicationIntro
        Mode.Login.ApplicationUidDisplay -> MinitelMode.Login.ApplicationUidDisplay
        Mode.Login.UinEntry -> MinitelMode.Login.UinEntry
        Mode.Login.Terminal -> MinitelMode.Login.Terminal
        is Mode.Shell -> MinitelMode.Shell(message = message)
        is Mode.Application -> MinitelMode.Application(questionNumber = questionNumber)
        is Mode.Notes -> MinitelMode.Notes(page = page)
        Mode.Cake -> MinitelMode.Cake
        Mode.BossKey -> MinitelMode.BossKey
    }

fun MinitelMode.toDomain(): Mode =
    when (this) {
        MinitelMode.Login.Initial -> Mode.Login.Initial
        MinitelMode.Login.Username -> Mode.Login.Username
        is MinitelMode.Login.Password -> Mode.Login.Password(isRetry = isRetry)
        MinitelMode.Login.Help -> Mode.Login.Help
        MinitelMode.Login.ApplicationIntro -> Mode.Login.ApplicationIntro
        MinitelMode.Login.ApplicationUidDisplay -> Mode.Login.ApplicationUidDisplay
        MinitelMode.Login.UinEntry -> Mode.Login.UinEntry
        MinitelMode.Login.Terminal -> Mode.Login.Terminal
        is MinitelMode.Shell -> Mode.Shell(message = message)
        is MinitelMode.Application -> Mode.Application(questionNumber = questionNumber)
        is MinitelMode.Notes -> Mode.Notes(page = page)
        MinitelMode.Cake -> Mode.Cake
        MinitelMode.BossKey -> Mode.BossKey
    }
