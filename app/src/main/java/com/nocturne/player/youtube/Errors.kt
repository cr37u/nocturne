package com.nocturne.player.youtube

import androidx.annotation.StringRes
import com.nocturne.player.R
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Friendly, user-facing error categories (Romanian strings). */
enum class ErrorKind(@StringRes val message: Int, val retryable: Boolean) {
    INVALID_LINK(R.string.error_invalid_link, false),
    NO_INTERNET(R.string.error_no_internet, true),
    UNAVAILABLE(R.string.error_unavailable, true),
    AGE_RESTRICTED(R.string.error_age_restricted, false),
    GEO_RESTRICTED(R.string.error_geo_restricted, false),
    PRIVATE(R.string.error_private, false),
    PAID(R.string.error_paid, false),
    CAPTCHA(R.string.error_captcha, true),
    BOT(R.string.error_bot, true),
    NO_AUDIO(R.string.error_no_audio, true),
    STORAGE(R.string.error_storage, false),
    GENERIC(R.string.error_generic, true);

    companion object {
        fun fromKey(key: String?): ErrorKind = entries.firstOrNull { it.name == key } ?: GENERIC
    }
}

class NoAudioStreamException : Exception("No audio-only stream")

class FriendlyException(val kind: ErrorKind, cause: Throwable? = null) : Exception(kind.name, cause)

fun Throwable.toErrorKind(): ErrorKind {
    var t: Throwable? = this
    while (t != null) {
        when (t) {
            is FriendlyException -> return t.kind
            is NoAudioStreamException -> return ErrorKind.NO_AUDIO
            is AgeRestrictedContentException -> return ErrorKind.AGE_RESTRICTED
            is GeographicRestrictionException, is UnsupportedContentInCountryException -> return ErrorKind.GEO_RESTRICTED
            is PrivateContentException -> return ErrorKind.PRIVATE
            is PaidContentException, is YoutubeMusicPremiumContentException -> return ErrorKind.PAID
            is SignInConfirmNotBotException -> return ErrorKind.BOT
            is ReCaptchaException -> return ErrorKind.CAPTCHA
            is AccountTerminatedException, is ContentNotAvailableException -> return ErrorKind.UNAVAILABLE
            is UnknownHostException, is ConnectException, is SSLException, is InterruptedIOException, is SocketException ->
                return ErrorKind.NO_INTERNET
            is IOException -> if (t.message?.contains("ENOSPC") == true || t.message?.contains("No space") == true) {
                return ErrorKind.STORAGE
            }
        }
        t = t.cause
    }
    return ErrorKind.GENERIC
}
