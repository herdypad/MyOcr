package com.mypro.myocr

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window

class LoadingDialog(context: Context) : Dialog(context) {

    init {
        setCancelable(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(R.layout.layout_loading)
    }
}