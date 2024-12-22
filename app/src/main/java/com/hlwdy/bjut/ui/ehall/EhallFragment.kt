package com.hlwdy.bjut.ui.ehall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hlwdy.bjut.BaseFragment
import com.hlwdy.bjut.databinding.FragmentEhallBinding

class EhallFragment : BaseFragment() {
    private var _binding: FragmentEhallBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEhallBinding.inflate(inflater, container, false)

        val root: View = binding.root

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}