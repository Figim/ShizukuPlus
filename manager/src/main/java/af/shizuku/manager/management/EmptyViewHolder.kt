package af.shizuku.manager.management

import af.shizuku.manager.databinding.AppListEmptyBinding
import android.view.LayoutInflater
import android.view.ViewGroup
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class EmptyViewHolder(
    private val binding: AppListEmptyBinding,
) : BaseViewHolder<Any>(binding.root) {
    companion object {
        @JvmField
        val CREATOR = Creator<Any> { inflater: LayoutInflater, parent: ViewGroup? -> EmptyViewHolder(AppListEmptyBinding.inflate(inflater, parent, false)) }
    }
}
