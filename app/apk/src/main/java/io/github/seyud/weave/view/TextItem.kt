package io.github.seyud.weave.view

import io.github.seyud.weave.R
import io.github.seyud.weave.databinding.DiffItem
import io.github.seyud.weave.databinding.ItemWrapper
import io.github.seyud.weave.databinding.RvItem

class TextItem(override val item: Int) : RvItem(), DiffItem<TextItem>, ItemWrapper<Int> {
    override val layoutRes = R.layout.item_text
}
