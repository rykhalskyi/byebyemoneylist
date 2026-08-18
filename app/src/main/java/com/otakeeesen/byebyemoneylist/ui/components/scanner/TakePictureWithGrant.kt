package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

class TakePictureWithGrant : ActivityResultContracts.TakePicture() {

    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
}
