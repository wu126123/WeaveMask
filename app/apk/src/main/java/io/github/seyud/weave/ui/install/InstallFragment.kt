package io.github.seyud.weave.ui.install

import io.github.seyud.weave.R
import io.github.seyud.weave.arch.BaseFragment
import io.github.seyud.weave.arch.viewModel
import io.github.seyud.weave.databinding.FragmentInstallMd2Binding
import io.github.seyud.weave.core.R as CoreR

class InstallFragment : BaseFragment<FragmentInstallMd2Binding>() {

    override val layoutRes = R.layout.fragment_install_md2
    override val viewModel by viewModel<InstallViewModel>()

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(CoreR.string.install)
    }
}
