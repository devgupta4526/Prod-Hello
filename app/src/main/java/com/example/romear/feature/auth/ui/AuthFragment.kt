package com.example.romear.feature.auth.ui

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.romear.BuildConfig
import com.example.romear.R
import com.example.romear.core.security.ProfilePhotoProcessor
import com.example.romear.databinding.FragmentAuthBinding
import com.example.romear.feature.auth.domain.OnboardingStep
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthFragment : Fragment() {
    private var binding: FragmentAuthBinding? = null
    private val viewModel: AuthViewModel by viewModels()
    private var previousStep: OnboardingStep? = null
    private var registered = false
    private var locationDenied = false
    private var previousError: String? = null

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) viewModel.locationFinished()
        else {
            locationDenied = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            binding?.primary?.setText(if (locationDenied) R.string.auth_settings else R.string.auth_allow_location)
        }
    }
    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.notificationsFinished() }
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val appContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                try { viewModel.upload(ProfilePhotoProcessor.prepare(appContext, uri)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { viewModel.photoFailure() }
            }
        }
    }
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION || viewModel.state.value.step != OnboardingStep.OTP) return
            val status = IntentCompat.getParcelableExtra(intent, SmsRetriever.EXTRA_STATUS, Status::class.java)
            if (status?.statusCode != CommonStatusCodes.SUCCESS) return
            val code = Regex("(?<![0-9])[0-9]{6}(?![0-9])").find(intent.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE).orEmpty())?.value ?: return
            binding?.otpInput?.setText(code)
            viewModel.verify(code)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentAuthBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = requireNotNull(binding)
        findNavController().getBackStackEntry(R.id.authFragment).savedStateHandle
            .getLiveData<Boolean>("welcome_login").observe(viewLifecycleOwner) { login ->
                viewModel.begin(login)
                findNavController().getBackStackEntry(R.id.authFragment).savedStateHandle.remove<Boolean>("welcome_login")
            }
        ui.phone.setText(viewModel.phone)
        ui.name.setText(viewModel.name)
        ui.bio.setText(viewModel.bio)
        ui.dob.setText(viewModel.dob)
        ui.phone.doAfterTextChanged { viewModel.phone = it.toString().filter(Char::isDigit) }
        ui.name.doAfterTextChanged { viewModel.name = it.toString() }
        ui.bio.doAfterTextChanged { viewModel.bio = it.toString() }
        ui.otpInput.doAfterTextChanged { value ->
            listOf(ui.otp1, ui.otp2, ui.otp3, ui.otp4, ui.otp5, ui.otp6).forEachIndexed { index, cell ->
                cell.text = value?.getOrNull(index)?.toString().orEmpty()
                cell.isSelected = index == (value?.length ?: 0)
            }
        }
        resources.getStringArray(R.array.profile_interests).forEach { interest ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = interest
                isCheckable = true
                setEnsureMinTouchTargetSize(true)
                isChecked = interest in viewModel.interests
                setOnCheckedChangeListener { _, checked ->
                    val selected = viewModel.interests.toMutableSet()
                    if (checked && selected.size >= 8 && interest !in selected) { isChecked = false; return@setOnCheckedChangeListener }
                    if (checked) selected.add(interest) else selected.remove(interest)
                    viewModel.interests = selected.toList()
                }
            }
            ui.interests.addView(chip)
        }
        ui.dob.setOnClickListener { showDatePicker() }
        ui.photoButton.setOnClickListener { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        ui.editPhone.setOnClickListener { ui.otpInput.text?.clear(); viewModel.editPhone() }
        ui.resend.setOnClickListener { ui.otpInput.text?.clear(); sendCode(resend = true) }
        ui.guidelinesCheck.setOnCheckedChangeListener { _, _ -> ui.primary.isEnabled = ui.guidelinesCheck.isChecked && !viewModel.state.value.loading }
        ui.primary.setOnClickListener { primaryAction() }
        ui.secondary.setOnClickListener {
            if (viewModel.state.value.step == OnboardingStep.LOCATION) viewModel.locationFinished()
            else if (viewModel.state.value.step == OnboardingStep.NOTIFICATIONS) viewModel.notificationsFinished()
        }
        ui.btnBack.setOnClickListener {
            if (viewModel.state.value.step == OnboardingStep.OTP) {
                ui.otpInput.text?.clear()
                viewModel.editPhone()
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        ui.preview.setOnClickListener { if (BuildConfig.DEBUG) enterApp() }
        ui.gallery.isVisible = false
        ui.gallery.setOnClickListener {
            if (BuildConfig.DEBUG) startActivity(Intent().setClassName(requireContext(), "com.example.romear.core.designsystem.DesignGalleryActivity"))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect { if (it == AuthEvent.ENTER_APP) enterApp() } }
            }
        }
    }

    private fun enterApp() {
        if (findNavController().currentDestination?.id == R.id.authFragment) findNavController().navigate(R.id.action_auth_to_discover)
    }
    private fun sendCode(resend: Boolean = false) {
        SmsRetriever.getClient(requireContext()).startSmsRetriever().addOnCompleteListener {
            if (binding != null) viewModel.requestCode(BuildConfig.SMS_APP_HASH.takeIf { hash -> hash.length == 11 }, resend)
        }
    }
    private fun showDatePicker() {
        if (childFragmentManager.findFragmentByTag("date_of_birth") != null) return
        val picker = MaterialDatePicker.Builder.datePicker().setTitleText(R.string.auth_dob_picker)
            .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build()).build()
        picker.addOnPositiveButtonClickListener { value ->
            viewModel.dob = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate().toString()
            binding?.dob?.setText(viewModel.dob)
        }
        picker.show(childFragmentManager, "date_of_birth")
    }
    private fun primaryAction() {
        when (viewModel.state.value.step) {
            OnboardingStep.SPLASH, OnboardingStep.MAINTENANCE -> viewModel.restore()
            OnboardingStep.WELCOME -> viewModel.begin()
            OnboardingStep.PHONE -> sendCode()
            OnboardingStep.OTP -> viewModel.verify(binding?.otpInput?.text.toString())
            OnboardingStep.DATE_OF_BIRTH -> viewModel.saveDob()
            OnboardingStep.PROFILE -> viewModel.saveProfile()
            OnboardingStep.GUIDELINES -> if (binding?.guidelinesCheck?.isChecked == true) viewModel.acceptGuidelines()
            OnboardingStep.LOCATION -> when {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> viewModel.locationFinished()
                locationDenied -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
                else -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            OnboardingStep.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.notificationsFinished()
            OnboardingStep.AVAILABILITY -> viewModel.finish(when (binding?.availability?.checkedRadioButtonId) { R.id.availability_off -> "OFF"; R.id.availability_browsing -> "BROWSING"; else -> "OPEN" })
            OnboardingStep.SUSPENDED, OnboardingStep.DELETION_PENDING -> viewModel.signOut()
            OnboardingStep.UPDATE -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID.removeSuffix(".debug").removeSuffix(".staging")}"))
                startActivity(intent)
            }
            OnboardingStep.COMPLETE -> enterApp()
            OnboardingStep.UNDER_AGE -> Unit
        }
    }

    private fun render(state: AuthUiState) {
        val ui = binding ?: return
        val step = state.step
        if (step == OnboardingStep.WELCOME && findNavController().currentDestination?.id == R.id.authFragment) {
            findNavController().navigate(R.id.welcomeFragment)
            return
        }
        val copy = when (step) {
            OnboardingStep.WELCOME -> Triple(R.string.welcome_title, R.string.welcome_body, R.string.auth_get_started)
            OnboardingStep.PHONE -> Triple(R.string.auth_phone_title, R.string.auth_phone_body, R.string.auth_send_code)
            OnboardingStep.OTP -> Triple(R.string.auth_otp_title, R.string.auth_otp_body, R.string.auth_verify)
            OnboardingStep.DATE_OF_BIRTH -> Triple(R.string.auth_dob_title, R.string.auth_dob_body, R.string.auth_continue)
            OnboardingStep.UNDER_AGE -> Triple(R.string.auth_under_age_title, R.string.auth_under_age_body, R.string.auth_continue)
            OnboardingStep.PROFILE -> Triple(R.string.auth_profile_title, R.string.auth_profile_body, R.string.auth_continue)
            OnboardingStep.GUIDELINES -> Triple(R.string.auth_guidelines_title, R.string.auth_guidelines_body, R.string.auth_guidelines_button)
            OnboardingStep.LOCATION -> Triple(R.string.auth_location_title, R.string.auth_location_body, R.string.auth_allow_location)
            OnboardingStep.NOTIFICATIONS -> Triple(R.string.auth_notifications_title, R.string.auth_notifications_body, R.string.auth_allow_notifications)
            OnboardingStep.AVAILABILITY -> Triple(R.string.auth_availability_title, R.string.auth_availability_body, R.string.auth_finish)
            OnboardingStep.SUSPENDED -> Triple(R.string.auth_suspended_title, R.string.auth_suspended_body, R.string.auth_sign_out)
            OnboardingStep.DELETION_PENDING -> Triple(R.string.auth_deletion_title, R.string.auth_deletion_body, R.string.auth_sign_out)
            OnboardingStep.UPDATE -> Triple(R.string.auth_update_title, R.string.auth_update_body, R.string.auth_continue)
            OnboardingStep.MAINTENANCE -> Triple(R.string.auth_maintenance_title, R.string.auth_maintenance_body, R.string.auth_retry)
            else -> Triple(R.string.auth_splash_title, R.string.auth_splash_body, R.string.auth_retry)
        }
        ui.authTitle.setText(copy.first)
        ui.authBody.text = if (step == OnboardingStep.OTP) getString(copy.second, viewModel.phone.takeLast(4)) else getString(copy.second)
        ui.primary.setText(if (step == OnboardingStep.LOCATION && locationDenied) R.string.auth_settings else copy.third)
        val index = when (step) { OnboardingStep.PHONE, OnboardingStep.OTP -> 1; OnboardingStep.DATE_OF_BIRTH -> 2; OnboardingStep.PROFILE -> 3; OnboardingStep.GUIDELINES -> 4; OnboardingStep.LOCATION -> 5; OnboardingStep.NOTIFICATIONS -> 6; OnboardingStep.AVAILABILITY -> 7; else -> 0 }
        ui.stepLabel.text = if (index > 0) getString(R.string.auth_step, index) else getString(R.string.welcome_eyebrow)
        when (step) {
            OnboardingStep.PHONE -> {
                ui.stepLabel.text = if (viewModel.loginMode) "ACCOUNT ACCESS" else "STEP 1 OF 3 · VERIFICATION"
                ui.authTitle.text = if (viewModel.loginMode) "Welcome back." else "What’s your number?"
                ui.authBody.text = if (viewModel.loginMode) "Your people and plans are waiting." else "We’ll text you a 6-digit code. We only use this to keep Romear bot-free."
                ui.primary.text = if (viewModel.loginMode) "Send login code" else "Send Verification Code →"
            }
            OnboardingStep.OTP -> {
                ui.stepLabel.text = "SECURITY CHECK"
                ui.authTitle.text = "Enter the 6-digit code"
                ui.authBody.text = "Sent to +91 ${viewModel.phone} · Test code: 123456"
                ui.primary.text = "Verify & Continue"
                if (ui.otpInput.text.isNullOrEmpty()) {
                    ui.otpInput.setText("123456")
                }
            }
            OnboardingStep.DATE_OF_BIRTH -> ui.stepLabel.text = "STEP 2 OF 3 · YOUR IDENTITY"
            else -> Unit
        }
        ui.wordmark.isVisible = false
        ui.btnBack.isVisible = step != OnboardingStep.SPLASH && step != OnboardingStep.WELCOME && step != OnboardingStep.COMPLETE
        when (step) {
            OnboardingStep.WELCOME -> {
                ui.hero.isVisible = true
                ui.hero.setImageResource(R.drawable.illustration_radar)
            }
            OnboardingStep.LOCATION -> {
                ui.hero.isVisible = true
                ui.hero.setImageResource(R.drawable.illustration_privacy_shield)
            }
            OnboardingStep.NOTIFICATIONS -> {
                ui.hero.isVisible = true
                ui.hero.setImageResource(R.drawable.illustration_verified_audio)
            }
            else -> {
                ui.hero.isVisible = false
            }
        }
        ui.phoneLayout.isVisible = step == OnboardingStep.PHONE
        ui.otpGroup.isVisible = step == OnboardingStep.OTP
        ui.dobLayout.isVisible = step == OnboardingStep.DATE_OF_BIRTH
        ui.profileGroup.isVisible = step == OnboardingStep.PROFILE
        ui.guidelinesCheck.isVisible = step == OnboardingStep.GUIDELINES
        ui.availability.isVisible = step == OnboardingStep.AVAILABILITY
        ui.secondary.isVisible = step == OnboardingStep.LOCATION || step == OnboardingStep.NOTIFICATIONS
        ui.preview.isVisible = false
        ui.primary.isVisible = step != OnboardingStep.UNDER_AGE
        ui.primary.isEnabled = !state.loading && (step != OnboardingStep.GUIDELINES || ui.guidelinesCheck.isChecked)
        ui.photoButton.isEnabled = !state.loading
        ui.photoButton.setText(if (state.hasPhoto) R.string.auth_photo_added else R.string.auth_add_photo)
        ui.resend.isEnabled = !state.loading && state.resendSeconds == 0
        ui.resend.text = if (state.resendSeconds > 0) getString(R.string.auth_resend_countdown, state.resendSeconds) else getString(R.string.auth_resend)
        ui.progress.isVisible = state.loading
        ui.loadingLabel.isVisible = state.loading
        ui.loadingLabel.setText(if (state.waking) R.string.service_waking else R.string.service_loading)
        ui.error.isVisible = state.error != null && step != OnboardingStep.UNDER_AGE
        ui.error.setText(errorText(state.error))
        if (step != previousStep) {
            if (ValueAnimator.areAnimatorsEnabled()) {
                ui.authTitle.alpha = 0f
                ui.authTitle.animate().alpha(1f).setDuration(180).start()
            }
            previousStep = step
        }
        if (state.error == "invalid_code" && state.error != previousError && ValueAnimator.areAnimatorsEnabled()) {
            ui.otpCells.animate().translationX(resources.getDimension(R.dimen.space_sm)).setDuration(60).withEndAction {
                binding?.otpCells?.animate()?.translationX(0f)?.setDuration(80)?.start()
            }.start()
        }
        previousError = state.error
    }
    private fun errorText(code: String?): Int = when (code) {
        "invalid_phone" -> R.string.auth_error_phone
        "invalid_code" -> R.string.auth_error_code
        "challenge_expired" -> R.string.auth_error_expired
        "locked" -> R.string.auth_error_locked
        "rate_limited" -> R.string.auth_error_rate
        "profile_incomplete", "onboarding_incomplete" -> R.string.auth_error_profile
        "storage_unavailable" -> R.string.auth_error_storage
        "invalid_photo", "photo_upload_failed" -> R.string.auth_error_photo
        "sms_unavailable" -> R.string.auth_error_sms
        "country_not_supported" -> R.string.auth_error_country
        "validation_failed" -> R.string.auth_error_date
        "conflict" -> R.string.auth_error_conflict
        "unauthorized" -> R.string.auth_error_unauthorized
        "photo_limit" -> R.string.auth_error_photo_limit
        "offline" -> R.string.service_offline
        "unconfigured" -> R.string.service_unconfigured
        else -> R.string.service_error
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(requireContext(), smsReceiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION), SmsRetriever.SEND_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }
    override fun onStop() {
        if (registered) requireContext().unregisterReceiver(smsReceiver)
        registered = false
        super.onStop()
    }
    override fun onDestroyView() {
        binding?.authTitle?.animate()?.cancel()
        binding?.otpCells?.animate()?.cancel()
        binding = null
        previousStep = null
        super.onDestroyView()
    }
}
