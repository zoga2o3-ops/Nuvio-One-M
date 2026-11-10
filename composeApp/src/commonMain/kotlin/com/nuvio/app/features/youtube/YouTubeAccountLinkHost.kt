package com.nuvio.app.features.youtube

/**
 * The YouTube account linked to the active Nuvio One profile, as a profile-settings record.
 * The Android app plugs in its storage; elsewhere there is nothing to link.
 */
object YouTubeAccountLinkHost {
    /** The linked account as JSON, or null when this profile has no YouTube account linked. */
    var export: (() -> String?)? = null

    /** Applies a linked account received from another device. */
    var apply: ((String) -> Unit)? = null

    /** Removes every linked account when the Nuvio One account signs out. */
    var clear: (() -> Unit)? = null
}
