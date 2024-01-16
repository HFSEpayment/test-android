package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.ss.halykepay.data.model.AuthConfig
import com.ss.halykepay.data.BuildType
import com.ss.halykepay.data.model.Invoice
import com.ss.halykepay.HalykEpaySDK
import com.ss.halykepay.data.model.CardIDReceiver
import com.ss.halykepay.data.model.CardIDSender
import com.ss.halykepay.data.model.CardId
import com.ss.halykepay.data.model.MasterPass
import com.ss.halykepay.data.model.MasterPassAction
import com.ss.halykepay.data.model.MasterPassCardData

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding  //defining the binding class
    private lateinit var halykEpaySdk: HalykEpaySDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater) //initializing the binding class
        setContentView(binding.root)

        binding.resetButton.setOnClickListener { reset() }

        binding.isRecurrentSwitch.isChecked = false

        binding.proceedButton.setOnClickListener {
            openPayment()
        }

        binding.p2pButton.setOnClickListener {
            openPayment(isP2P = true)
        }

        binding.aftButton.setOnClickListener {
            openPayment(isAFT = true)
        }

        binding.octButton.setOnClickListener {
            openPayment(isOCT = true)
        }

        binding.cardID.setOnClickListener {
            openPayment(
                cardID = CardId(
                    sender = CardIDSender(
                        cardNumber = "440563******5096",
                        cardHolder = "John Johnson",
                        cardCred = "f908a460-f640-ea29-e053-1d1a000ab1de"
                    ),
                    receiver = CardIDReceiver(
                        cardCred = "ec89e681-cd17-1a3d-e053-1d1a000a54cf",
                        cardNumber = "400303******9821"
                    )
                )
            )
        }

        binding.p2pPhone.setOnClickListener {
            openPayment(isP2PPhone = true)
        }

        binding.debugRadioButton.setOnCheckedChangeListener { _, _ ->
            reset()
        }
        binding.preprodRadioButton.setOnCheckedChangeListener { _, _ ->
            reset()
        }
        binding.prodRadioButton.setOnCheckedChangeListener { _, _ ->
            reset()
        }

        binding.masterPass.setOnClickListener {
            openPayment(
                masterPass = MasterPass(
                    token = "93e20834-f900-4e5c-8c31-160605aa522d",
                    merchantName = "KcellEpay",
                    session = "1b68b927-ce8b-42c0-bc6d-bd43de4e49c",
                    isClientParticipation = true,
                    isVisible = true,
                    MasterPassAction = MasterPassAction(
                        SaveCard = true,
                        updateSaveCard = false,
                        recurring = false
                    )
                )
            )
        }
        binding.debugRadioButton.isChecked = true

        binding.monthlyRadioButton.isChecked = true
    }

    private fun openPayment(
        isP2P: Boolean = false,
        isAFT: Boolean = false,
        isOCT: Boolean = false,
        cardID: CardId? = null,
        isP2PPhone: Boolean = false,
        masterPass: MasterPass? = null
    ) {
        val config = AuthConfig(
            merchantID = binding.merchantIdEditText.text.toString().trim(),
            merchantName = binding.merchantNameEditText.text.toString().trim(),
            clientID = binding.clientIdEditText.text.toString().trim(),
            clientSecret = binding.clientSecretEditText.text.toString().trim()
        )

        val invoice = Invoice(
            id = binding.invoiceIdEditText.text.toString().trim(),
            amount = binding.amountEditText.text.toString().trim().toDoubleOrNull() ?: 100.0,
            currency = "KZT",
            accountID = "uuid023001",
            description = "test payment",
            postLink = "https://testmerchant/order/1123",
            failurePostLink = "https://testmerchant/order/1123/fail",
            isRecurrent = binding.isRecurrentSwitch.isChecked,
            autoPaymentFrequency = getAutopaymentFrequency(),
            homebankToken = "9FZYVXC4PZCSUFBKQ5PZKW",
            isP2P = isP2P,
            isAFT = isAFT,
            isOCT = isOCT,
            cardId = cardID,
            isP2PPhone = isP2PPhone,
            masterPass = masterPass
        )

        halykEpaySdk = HalykEpaySDK.instance(
            this,
            config,
            buildType = getBuildType()
        ).apply {
            launchEpay(invoice)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == HalykEpaySDK.EPAY_SDK_REQUEST_CODE) {
            val result = halykEpaySdk.getPaymentResult(requestCode, resultCode, data)

            if (result.isSuccessful) {
                binding.resultTextView.text = result.paymentReference ?: "no payment reference"
                binding.cardIdTextView.text = "cardID: ${result.cardID}"
            } else {
                binding.resultTextView.text = result.errorMessage ?: "unknown error"
            }
        }
    }

    private fun setDebugMode() {
        binding.merchantIdEditText.setText(DEBUG_MERCHANT_ID)
        binding.merchantNameEditText.setText(DEBUG_MERCHANT_NAME)
        binding.clientIdEditText.setText(DEBUG_CLIENT_ID)
        binding.clientSecretEditText.setText(DEBUG_CLIENT_SECRET)
        binding.invoiceIdEditText.setText("53124740174854")
        binding.amountEditText.setText(55.toString())
    }

    private fun setReleaseMode() {
        binding.merchantIdEditText.setText(PROD_MERCHANT_ID)
        binding.merchantNameEditText.setText(PROD_MERCHANT_NAME)
        binding.clientIdEditText.setText(PROD_CLIENT_ID)
        binding.clientSecretEditText.setText(PROD_CLIENT_SECRET)
        binding.invoiceIdEditText.setText("")
        binding.amountEditText.setText(5.toString())
    }

    private fun reset() {
        when (getBuildType()) {
            BuildType.DEBUG -> setDebugMode()
            BuildType.PREPROD -> setReleaseMode()
            BuildType.PROD -> setReleaseMode()
        }
    }

    private fun getAutopaymentFrequency(): Invoice.AutoPaymentFrequency {
        return when {
            binding.weeklyRadioButton.isChecked -> Invoice.AutoPaymentFrequency.WEEKLY
            binding.monthlyRadioButton.isChecked -> Invoice.AutoPaymentFrequency.MONTHLY
            binding.quarterlyRadioButton.isChecked -> Invoice.AutoPaymentFrequency.QUARTERLY
            else -> Invoice.AutoPaymentFrequency.MONTHLY
        }
    }

    private fun getBuildType(): BuildType {
        return when {
            binding.debugRadioButton.isChecked -> BuildType.DEBUG
            binding.preprodRadioButton.isChecked -> BuildType.PREPROD
            binding.prodRadioButton.isChecked -> BuildType.PROD
            else -> BuildType.DEBUG
        }
    }


    companion object {
        private const val DEBUG_MERCHANT_ID = "67e34d63-102f-4bd1-898e-370781d0074d"
        private const val DEBUG_MERCHANT_NAME = "UberFlower"
        private const val DEBUG_CLIENT_ID = "test"
        private const val DEBUG_CLIENT_SECRET = "yF587AV9Ms94qN2QShFzVR3vFnWkhjbAK3sG"

        private const val PROD_MERCHANT_ID = ""
        private const val PROD_MERCHANT_NAME = ""
        private const val PROD_CLIENT_ID = ""
        private const val PROD_CLIENT_SECRET = ""
    }
}
