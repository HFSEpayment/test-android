package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.ActivityMainBinding
import com.ss.halykepay.HalykEpaySDK
import com.ss.halykepay.data.BuildType
import com.ss.halykepay.data.model.AuthConfig
import com.ss.halykepay.data.model.CardIDReceiver
import com.ss.halykepay.data.model.CardIDSender
import com.ss.halykepay.data.model.CardId
import com.ss.halykepay.data.model.Invoice
import com.ss.halykepay.data.model.MasterPass
import com.ss.halykepay.data.model.MasterPassAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "HalykPayDebug"
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var halykEpaySdk: HalykEpaySDK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initDefaultValues()
        initClickListeners()
    }

    private fun initDefaultValues() {
        binding.isRecurrentSwitch.isChecked = false
        binding.debugRadioButton.isChecked = true
        binding.monthlyRadioButton.isChecked = true
        reset()
    }

    private fun initClickListeners() {
        binding.resetButton.setOnClickListener { reset() }

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

        binding.p2pPhone.setOnClickListener {
            openPayment(isP2PPhone = true)
        }

        binding.cardID.setOnClickListener {
            openPayment(cardID = buildCardId())
        }

        binding.masterPass.setOnClickListener {
            openPayment(masterPass = buildMasterPass())
        }

        binding.debugRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) reset()
        }

        binding.preprodRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) reset()
        }

        binding.prodRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) reset()
        }
    }

    private fun openPayment(
        isP2P: Boolean = false,
        isAFT: Boolean = false,
        isOCT: Boolean = false,
        cardID: CardId? = null,
        isP2PPhone: Boolean = false,
        masterPass: MasterPass? = null
    ) {
        lifecycleScope.launch {
            val paymentParams = PaymentParams(
                isP2P = isP2P,
                isAFT = isAFT,
                isOCT = isOCT,
                cardID = cardID,
                isP2PPhone = isP2PPhone,
                masterPass = masterPass
            )

            val token = getAccessToken(buildTokenParams())

            if (token.isNullOrBlank()) {
                showToast("Не удалось получить токен")
                return@launch
            }

            val config = buildAuthConfig(token)
            val invoice = buildInvoice(paymentParams)

            startEpay(config, invoice)
        }
    }

    private fun buildTokenParams(): EpayTokenParams {
        return EpayTokenParams(
            tokenUrl = getTokenUrl(),
            clientId = CLIENT_ID,
            clientSecret = CLIENT_SECRET,
            invoiceId = getInvoiceId(),
            amount = getAmount(),
            terminal = getTerminalId()
        )
    }

    private suspend fun getAccessToken(params: EpayTokenParams): String? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d(TAG, "Starting token request to: ${params.tokenUrl}")

                val conn = URL(params.tokenUrl).openConnection() as HttpURLConnection

                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Accept", "application/json")

                val formBody = mapOf(
                    "grant_type" to "client_credentials",
                    "scope" to params.scope,
                    "client_id" to params.clientId,
                    "client_secret" to params.clientSecret,
                    "invoiceID" to params.invoiceId,
                    "amount" to params.amount.toString(),
                    "currency" to params.currency,
                    "terminal" to params.terminal,
                    "is_credit" to params.isCredit,
                    "secret_hash" to params.secretHash
                ).toFormUrlEncoded()

                conn.outputStream.use { outputStream ->
                    outputStream.write(formBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val responseBody = readResponseBody(conn, responseCode)

                if (responseCode !in 200..299 || responseBody.isBlank()) {
                    android.util.Log.e(TAG, "Token error: HTTP $responseCode, body: $responseBody")
                    return@withContext null
                }

                android.util.Log.d(TAG, "Token success response: $responseBody")

                JSONObject(responseBody)
                    .optString("access_token")
                    .takeIf { it.isNotBlank() }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Exception during getAccessToken execution", e)
                null
            }
        }
    }

    private fun readResponseBody(conn: HttpURLConnection, responseCode: Int): String {
        return if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
    }

    private fun buildAuthConfig(token: String): AuthConfig {
        return AuthConfig(
            terminalId = getTerminalId(),
            merchantName = getMerchantName(),
            authToken = token
        )
    }

    private fun buildInvoice(params: PaymentParams): Invoice {
        return Invoice(
            id = getInvoiceId(),
            amount = getAmount(),
            currency = CURRENCY,
            accountID = ACCOUNT_ID,
            description = DESCRIPTION,
            postLink = POST_LINK,
            failurePostLink = FAILURE_POST_LINK,
            isRecurrent = binding.isRecurrentSwitch.isChecked,
            autoPaymentFrequency = getAutopaymentFrequency(),
            homebankToken = HOMEBANK_TOKEN,
            isP2P = params.isP2P,
            isAFT = params.isAFT,
            isOCT = params.isOCT,
            cardId = params.cardID,
            isP2PPhone = params.isP2PPhone,
            masterPass = params.masterPass
        )
    }

    private fun startEpay(config: AuthConfig, invoice: Invoice) {
        halykEpaySdk = HalykEpaySDK.instance(
            this@MainActivity,
            config,
            buildType = getBuildType()
        )

        halykEpaySdk.launchEpay(invoice)
    }

    private fun buildCardId(): CardId {
        return CardId(
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
    }

    private fun buildMasterPass(): MasterPass {
        return MasterPass(
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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != HalykEpaySDK.EPAY_SDK_REQUEST_CODE) return

        val result = halykEpaySdk.getPaymentResult(requestCode, resultCode, data)

        if (result.isSuccessful) {
            binding.resultTextView.text = result.paymentReference ?: "no payment reference"
            binding.cardIdTextView.text = "cardID: ${result.cardID}"
        } else {
            binding.resultTextView.text = result.errorMessage ?: "unknown error"
        }
    }

    private fun setDebugMode() {
        binding.merchantIdEditText.setText(DEBUG_TERMINAL)
        binding.merchantNameEditText.setText(DEBUG_MERCHANT_NAME)
        binding.invoiceIdEditText.setText("53124740174854")
        binding.amountEditText.setText("100")
    }

    private fun setReleaseMode() {
        binding.merchantIdEditText.setText(PROD_TERMINAL)
        binding.merchantNameEditText.setText(PROD_MERCHANT_NAME)
        binding.invoiceIdEditText.setText("")
        binding.amountEditText.setText("5")
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

    private fun getTokenUrl(): String {
        return when (getBuildType()) {
            BuildType.DEBUG -> DEBUG_TOKEN_URL
            BuildType.PREPROD -> PREPROD_TOKEN_URL
            BuildType.PROD -> PROD_TOKEN_URL
        }
    }

    private fun getInvoiceId(): String {
        return binding.invoiceIdEditText.text.toString().trim()
    }

    private fun getAmount(): Double {
        return binding.amountEditText.text.toString().trim().toDoubleOrNull() ?: DEFAULT_AMOUNT
    }

    private fun getTerminalId(): String {
        return binding.merchantIdEditText.text.toString().trim()
    }

    private fun getMerchantName(): String {
        return binding.merchantNameEditText.text.toString().trim()
    }

    private fun showToast(message: String) {
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
    }

    private fun Map<String, String>.toFormUrlEncoded(): String {
        return entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
    }

    private data class PaymentParams(
        val isP2P: Boolean = false,
        val isAFT: Boolean = false,
        val isOCT: Boolean = false,
        val cardID: CardId? = null,
        val isP2PPhone: Boolean = false,
        val masterPass: MasterPass? = null
    )

    private data class EpayTokenParams(
        val tokenUrl: String,
        val clientId: String,
        val clientSecret: String,
        val invoiceId: String,
        val amount: Double,
        val currency: String = CURRENCY,
        val terminal: String,
        val scope: String = "payment usermanagement",
        val isCredit: String = "N",
        val secretHash: String = ""
    )

    companion object {
        private const val DEBUG_TERMINAL = "67e34d63-102f-4bd1-898e-370781d0074d"
        private const val DEBUG_MERCHANT_NAME = "UberFlower"

        private const val PROD_TERMINAL = ""
        private const val PROD_MERCHANT_NAME = ""

        private const val DEBUG_TOKEN_URL = "https://test-epay-oauth.epayment.kz/oauth2/token"
        private const val PREPROD_TOKEN_URL = "https://test-epay-oauth.epayment.kz/oauth2/token"
        private const val PROD_TOKEN_URL = "https://epay-oauth.homebank.kz/oauth2/token"

        private const val CLIENT_ID = "test"
        private const val CLIENT_SECRET = "yF587AV9Ms94qN2QShFzVR3vFnWkhjbAK3sG"

        private const val CURRENCY = "KZT"
        private const val ACCOUNT_ID = "uuid023001"
        private const val DESCRIPTION = "test payment"
        private const val POST_LINK = "https://testmerchant/order/1123"
        private const val FAILURE_POST_LINK = "https://testmerchant/order/1123/fail"
        private const val HOMEBANK_TOKEN = "9FZYVXC4PZCSUFBKQ5PZKW"

        private const val DEFAULT_AMOUNT = 100.0
    }
}