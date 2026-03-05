package io.github.seyud.weave.core.utils

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * 设备身份验证请求契约
 * 用于启动系统锁屏凭据确认对话框
 */
class RequestAuthentication : ActivityResultContract<Unit, Boolean>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return keyguardManager.createConfirmDeviceCredentialIntent(null, null)!!
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}
