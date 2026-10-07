package com.facefit.kiosk.ui.welcome

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import com.facefit.kiosk.R
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenKioskWelcomeBinding
import com.facefit.kiosk.databinding.ViewWelcomeStepCardBinding
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator

class KioskWelcomeView(
    context: Context,
    private val sessionStore: KioskSessionStore,
    private val navigator: KioskNavigator
) : FrameLayout(context) {

    private val binding = ScreenKioskWelcomeBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        bindShopState()
        bindStepCards()
        binding.startScanButton.setOnClickListener { startScanFlow() }
        binding.touchStartPanel.setOnClickListener { startScanFlow() }
        binding.browseFramesButton.setOnClickListener {
            Toast.makeText(context, R.string.welcome_browse_not_ready, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindShopState() {
        val shop = requireNotNull(sessionStore.activeShop)
        binding.shopNameText.text = context.getString(R.string.welcome_store_line, shop.displayName)
    }

    private fun bindStepCards() {
        bindStep(
            binding.scanStep,
            number = "1",
            title = context.getString(R.string.welcome_step_scan_title),
            body = context.getString(R.string.welcome_step_scan_body)
        )
        bindStep(
            binding.analyzeStep,
            number = "2",
            title = context.getString(R.string.welcome_step_analyze_title),
            body = context.getString(R.string.welcome_step_analyze_body)
        )
        bindStep(
            binding.matchStep,
            number = "3",
            title = context.getString(R.string.welcome_step_match_title),
            body = context.getString(R.string.welcome_step_match_body)
        )
    }

    private fun bindStep(
        stepBinding: ViewWelcomeStepCardBinding,
        number: String,
        title: String,
        body: String
    ) {
        stepBinding.stepNumberText.text = number
        stepBinding.stepTitleText.text = title
        stepBinding.stepBodyText.text = body
    }

    private fun startScanFlow() {
        navigator.navigateTo(KioskDestination.Consent)
    }
}
