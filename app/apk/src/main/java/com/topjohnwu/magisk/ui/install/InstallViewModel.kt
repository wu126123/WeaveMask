package com.topjohnwu.magisk.ui.install

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.Spanned
import android.text.SpannedString
import androidx.databinding.Bindable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.BuildConfig.APP_VERSION_CODE
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.repository.NetworkService
import com.topjohnwu.magisk.databinding.set
import com.topjohnwu.magisk.dialog.SecondSlotWarningDialog
import com.topjohnwu.magisk.ui.flash.FlashFragment
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.File
import java.io.IOException
import com.topjohnwu.magisk.core.R as CoreR

class InstallViewModel(svc: NetworkService, markwon: Markwon) : BaseViewModel() {

    val isRooted get() = Info.isRooted
    val skipOptions = Info.isEmulator || (Info.isSAR && !Info.isFDE && Info.ramdisk)
    val noSecondSlot = !isRooted || !Info.isAB || Info.isEmulator

    @get:Bindable
    var step = if (skipOptions) 1 else 0
        set(value) = set(value, field, { field = it }, BR.step)

    private var methodId = -1

    @get:Bindable
    var method
        get() = methodId
        set(value) = set(value, methodId, { methodId = it }, BR.method) {
            when (it) {
                R.id.method_inactive_slot -> {
                    SecondSlotWarningDialog().show()
                }
            }
        }

    val data: LiveData<Uri?> get() = uri

    fun setPatchFile(localUri: Uri) {
        uri.value = localUri
    }

    @get:Bindable
    var notes: Spanned = SpannedString("")
        set(value) = set(value, field, { field = it }, BR.notes)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val noteFile = File(AppContext.cacheDir, "${APP_VERSION_CODE}.md")
                val noteText = when {
                    noteFile.exists() -> noteFile.readText()
                    else -> {
                        val note = svc.fetchUpdate(APP_VERSION_CODE)?.note.orEmpty()
                        if (note.isEmpty()) return@launch
                        noteFile.writeText(note)
                        note
                    }
                }
                val spanned = markwon.toMarkdown(noteText)
                withContext(Dispatchers.Main) {
                    notes = spanned
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }
    }

    fun install() {
        when (method) {
            R.id.method_patch -> FlashFragment.patch(data.value!!).navigate(true)
            R.id.method_direct -> FlashFragment.flash(false).navigate(true)
            R.id.method_inactive_slot -> FlashFragment.flash(true).navigate(true)
            else -> error("Unknown value")
        }
    }

    fun composeFlashRequest(): ComposeFlashRequest? {
        return when (method) {
            R.id.method_patch -> data.value?.let {
                ComposeFlashRequest(
                    action = Const.Value.PATCH_FILE,
                    dataUri = it
                )
            }

            R.id.method_direct -> ComposeFlashRequest(
                action = Const.Value.FLASH_MAGISK,
                dataUri = null
            )

            R.id.method_inactive_slot -> ComposeFlashRequest(
                action = Const.Value.FLASH_INACTIVE_SLOT,
                dataUri = null
            )

            else -> null
        }
    }

    override fun onSaveState(state: Bundle) {
        state.putParcelable(
            INSTALL_STATE_KEY, InstallState(
                methodId,
                step,
                Config.keepVerity,
                Config.keepEnc,
                Config.recovery
            )
        )
    }

    override fun onRestoreState(state: Bundle) {
        state.getParcelable<InstallState>(INSTALL_STATE_KEY)?.let {
            methodId = it.method
            step = it.step
            Config.keepVerity = it.keepVerity
            Config.keepEnc = it.keepEnc
            Config.recovery = it.recovery
        }
    }

    @Parcelize
    class InstallState(
        val method: Int,
        val step: Int,
        val keepVerity: Boolean,
        val keepEnc: Boolean,
        val recovery: Boolean,
    ) : Parcelable

    data class ComposeFlashRequest(
        val action: String,
        val dataUri: Uri?
    )

    companion object {
        private const val INSTALL_STATE_KEY = "install_state"
        private val uri = MutableLiveData<Uri?>()
    }
}
