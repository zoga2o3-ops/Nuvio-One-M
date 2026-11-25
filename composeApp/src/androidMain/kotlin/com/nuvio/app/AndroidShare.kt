package com.nuvio.app

import android.content.Context
import android.content.Intent

/** Opens Android's native Sharesheet with plain text, such as a public video or track link. */
internal fun sharePlainText(context: Context, chooserTitle: String, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.applicationContext.startActivity(
        Intent.createChooser(sendIntent, chooserTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
