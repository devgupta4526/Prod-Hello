package com.example.romear.feature.onboarding.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.romear.R
import com.example.romear.databinding.FragmentWelcomeBinding

data class WelcomeSlide(
    val imageRes: Int,
    val title: String,
    val description: String
)

class WelcomeFragment : Fragment() {
    private var binding: FragmentWelcomeBinding? = null

    private val slides = listOf(
        WelcomeSlide(
            imageRes = R.drawable.illustration_radar,
            title = "Real connections.\nRight around you.",
            description = "No endless swiping. Connect with people open to meeting or chatting in your immediate radius."
        ),
        WelcomeSlide(
            imageRes = R.drawable.illustration_privacy_shield,
            title = "Privacy first.\nAlways fuzzed.",
            description = "Your exact coordinates are never shown. Only approximate radius and neighbourhood."
        ),
        WelcomeSlide(
            imageRes = R.drawable.illustration_verified_audio,
            title = "Real voices.\nVerified profiles.",
            description = "15-second voice bios and IRL selfie verification to eliminate bots and catfishes."
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentWelcomeBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = binding ?: return

        val adapter = WelcomeSlideAdapter(slides)
        ui.welcomePager.adapter = adapter

        ui.welcomePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
            }
        })

        val navigateToAuth: (Boolean) -> Unit = { login ->
            if (findNavController().currentDestination?.id == R.id.welcomeFragment) {
                val controller = findNavController()
                if (controller.previousBackStackEntry?.destination?.id == R.id.authFragment) {
                    controller.previousBackStackEntry?.savedStateHandle?.set("welcome_login", login)
                    controller.popBackStack()
                } else {
                    controller.navigate(R.id.authFragment, null, androidx.navigation.NavOptions.Builder().setPopUpTo(R.id.welcomeFragment, true).build())
                }
            }
        }

        ui.btnGetStarted.setOnClickListener {
            navigateToAuth(false)
        }

        ui.exploreButton.setOnClickListener { navigateToAuth(false) }
        ui.btnSkip.setOnClickListener { navigateToAuth(false) }
        ui.btnAlreadyAccount.setOnClickListener { navigateToAuth(true) }
    }

    private fun updateIndicators(position: Int) {
        val ui = binding ?: return
        val dots = listOf(ui.dot1, ui.dot2, ui.dot3)
        dots.forEachIndexed { index, dot ->
            val isActive = index == position
            dot.setBackgroundResource(if (isActive) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
            val params = dot.layoutParams
            params.width = ((if (isActive) 24 else 6) * resources.displayMetrics.density).toInt()
            params.height = (6 * resources.displayMetrics.density).toInt()
            dot.layoutParams = params
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

class WelcomeSlideAdapter(private val items: List<WelcomeSlide>) :
    RecyclerView.Adapter<WelcomeSlideAdapter.SlideViewHolder>() {

    class SlideViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.slide_image)
        val title: TextView = view.findViewById(R.id.slide_title)
        val desc: TextView = view.findViewById(R.id.slide_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_welcome_slide, parent, false)
        return SlideViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        val item = items[position]
        holder.image.setImageResource(item.imageRes)
        holder.title.text = item.title
        holder.desc.text = item.description
    }

    override fun getItemCount(): Int = items.size
}
