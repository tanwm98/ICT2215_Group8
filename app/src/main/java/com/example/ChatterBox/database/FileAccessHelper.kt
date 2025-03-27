package com.example.ChatterBox.database

import android.content.Context
import java.io.File

object FileAccessHelper {
    fun getStorageDir(context: Context, dirType: String): File {
        val dir = context.getDir(dirType, Context.MODE_PRIVATE)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}