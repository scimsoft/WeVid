package com.scimsoft.wevid.ui.nav

import android.net.Uri

object Routes {
    const val SignIn = "sign_in"
    const val Onboarding = "onboarding"
    const val Feed = "feed"
    const val Chats = "chats"
    const val Settings = "settings"

    const val Thread = "thread/{chatId}?title={title}"
    fun thread(chatId: String, title: String): String =
        "thread/$chatId?title=${Uri.encode(title)}"

    /** Optional chatId: omit (or empty) for a location feed post. */
    const val Record = "record?chatId={chatId}"
    fun record(chatId: String): String = "record?chatId=${Uri.encode(chatId)}"
    fun recordPost(): String = "record?chatId="
}
