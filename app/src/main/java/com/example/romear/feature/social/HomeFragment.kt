package com.example.romear.feature.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    @Inject lateinit var repository: SocialRepository
    private lateinit var social: SocialUi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        social = SocialUi(this, repository)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ScrollView(requireContext()).apply {
            addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL })
        }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val column = (view as ScrollView).getChildAt(0) as LinearLayout
        column.addView(TextView(requireContext()).apply { text = "Your week"; textSize = 28f; setPadding(24,24,24,12) })
        column.addView(MaterialButton(requireContext()).apply { text = "Load my home"; setOnClickListener { social.home(column) } })
        social.home(column)
    }
}
