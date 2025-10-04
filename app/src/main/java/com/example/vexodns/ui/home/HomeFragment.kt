package com.example.vexodns.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vexodns.R
import com.example.vexodns.data.SubscriptionData
import com.example.vexodns.data.UpdateIpRequest
import com.example.vexodns.databinding.FragmentHomeBinding
import com.example.vexodns.network.ApiService
import kotlinx.coroutines.launch
import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.text.DecimalFormat
import android.os.CountDownTimer
import android.widget.AdapterView
import com.example.vexodns.MainActivity
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.VpnService
import com.example.vexodns.service.DnsVpnService
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build


class HomeFragment : Fragment() {
    private lateinit var vpnStateReceiver: BroadcastReceiver
    private var isConnected = false
    private var selectedDnsType: String = "Do53"
    // Add these to the top of the HomeFragment class
    private var lastSubscriptionData: SubscriptionData? = null
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(requireContext(), "Permission for VPN was denied", Toast.LENGTH_SHORT).show()
        }
    }
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // یک نمونه از ApiService برای استفاده در کل کلاس
    private val apiService: ApiService by lazy {
        // Create a lenient Gson instance
        val gson = GsonBuilder()
            .setLenient()
            .create()

        Retrofit.Builder()
            .baseUrl("https://example.com/")
            .addConverterFactory(GsonConverterFactory.create(gson)) // Use the new lenient Gson
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
    private fun resetConnectionState() {
        val sharedPref = activity?.getSharedPreferences("VexoDNSPrefs", Context.MODE_PRIVATE)
        isConnected = false
        sharedPref?.edit { putBoolean("is_connected_state", false) }
        updateButtonState()
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.fetchButton.setOnClickListener {
            lifecycleScope.launch {
                fetchSubscriptionData(showErrors = true)
            }
        }
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadLastData()

        val sharedPref = activity?.getSharedPreferences("VexoDNSPrefs", Context.MODE_PRIVATE)
        isConnected = sharedPref?.getBoolean("is_connected_state", false) ?: false

        updateButtonState()

        // --- New Fetch Button Logic ---
        binding.fetchButton.setOnClickListener {
            lifecycleScope.launch {
                fetchSubscriptionData(showErrors = true)
            }
        }

        // --- New Connect Button Logic ---
        binding.connectButtonLayout.setOnClickListener {
            // First, check if the right DNS type is selected
            if (selectedDnsType != "Do53") {
                Toast.makeText(requireContext(), "این قابلیت هنوز پیاده‌سازی نشده است", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Toggle connection state
            isConnected = !isConnected
            sharedPref?.edit { putBoolean("is_connected_state", isConnected) }

            // If user wants to connect, start the check process
            if (isConnected) {
                binding.connectStatusText.text = getString(R.string.fetch_button_loading)

                lifecycleScope.launch {
                    val result = fetchSubscriptionData(showErrors = true)

                    // Now check the result before connecting
                    if (result.wasSuccessful && result.subscriptionData != null) {
                        if (result.subscriptionData.statusKey != "table_status_active") {
                            Toast.makeText(requireContext(), "اشتراک شما فعال نیست", Toast.LENGTH_LONG).show()
                            resetConnectionState() // Revert the button
                        } else if (result.needsCountdown) {
                            startCountdown()
                            Toast.makeText(requireContext(), "IP در حال آپدیت است، پس از اتمام تایمر دوباره تلاش کنید", Toast.LENGTH_LONG).show()
                            resetConnectionState() // Revert the button
                        } else {
                            // Everything is OK, proceed with connection
                            updateButtonState()
                        }
                    } else {
                        // Fetch failed, revert the button
                        resetConnectionState()
                    }
                }
            } else {
                // If user wants to disconnect, just do it
                updateButtonState()
            }
        }
        vpnStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == DnsVpnService.BROADCAST_VPN_STATE) {
                    // The service has stopped, so update the UI
                    if (isConnected) {
                        binding.connectButtonLayout.performClick()
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(vpnStateReceiver, IntentFilter(DnsVpnService.BROADCAST_VPN_STATE), Context.RECEIVER_NOT_EXPORTED)
        } else       {
              requireActivity().registerReceiver(vpnStateReceiver, IntentFilter(DnsVpnService.BROADCAST_VPN_STATE))
        }
        binding.dnsTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedDnsType = parent?.getItemAtPosition(position).toString()
                // اینجا می‌توانید منطق خود را اضافه کنید، فعلاً یک پیام Toast نمایش می‌دهیم
                Toast.makeText(requireContext(), "Selected: $selectedDnsType", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // این تابع الزامی است، اما می‌توانیم آن را خالی بگذاریم.
            }
        }
    }
    private fun updateButtonState() {
        if (isConnected) {
            binding.connectButtonLayout.setBackgroundResource(R.drawable.button_background_connected)
            binding.connectStatusText.text = getString(R.string.status_connected)
            startVpnService()
        } else {
            binding.connectButtonLayout.setBackgroundResource(R.drawable.button_background_disconnected)
            binding.connectStatusText.text = getString(R.string.status_not_connected)
            stopVpnService()
        }
    }
    private fun startVpnService() {
        val dnsIp = lastSubscriptionData?.douIp1 // From your Python code logic
        if (dnsIp == null) {
            Toast.makeText(requireContext(), "DNS IP not available from subscription", Toast.LENGTH_SHORT).show()
            // Reset button state if IP is not available
            isConnected = false
            binding.connectButtonLayout.setBackgroundResource(R.drawable.button_background_disconnected)
            binding.connectStatusText.text = getString(R.string.status_not_connected)
            return
        }

        val vpnIntent = VpnService.prepare(requireContext())
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            // Permission already granted, start the service
            val intent = Intent(requireContext(), DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_CONNECT
                putExtra("DNS_IP", dnsIp)
            }
            requireActivity().startService(intent)
        }
    }

    private fun stopVpnService() {
        val intent = Intent(requireContext(), DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_DISCONNECT
        }
        requireActivity().startService(intent)
    }
    private fun loadLastData() {
        val sharedPref = activity?.getSharedPreferences("VexoDNSPrefs", Context.MODE_PRIVATE) ?: return
        val lastDataJson = sharedPref.getString("last_sub_data", null)
        if (lastDataJson != null) {
            val gson = Gson()
            val lastData = gson.fromJson(lastDataJson, SubscriptionData::class.java)
            updateUi(lastData)
        }
    }
    // A new data class to hold the result of our fetch operation
    private data class FetchResult(
        val wasSuccessful: Boolean,
        val needsCountdown: Boolean,
        val subscriptionData: SubscriptionData?
    )

    private suspend fun fetchSubscriptionData(showErrors: Boolean = true): FetchResult {
        val sharedPref = activity?.getSharedPreferences("VexoDNSPrefs", Context.MODE_PRIVATE) ?: return FetchResult(false, false, null)
        val originalUrl = sharedPref.getString("subscription_link", null)

        if (originalUrl.isNullOrBlank()) {
            if (showErrors) Toast.makeText(context, getString(R.string.warning_text), Toast.LENGTH_LONG).show()
            return FetchResult(false, false, null)
        }

        requireActivity().runOnUiThread {
            binding.progressBar.isVisible = true
            binding.resultCard.isVisible = false
            binding.statusBarText.text = getString(R.string.connecting_status)
        }

        try {
            var subApiUrl = originalUrl
            if ("/sub/" in originalUrl && "/api/sub/" !in originalUrl) {
                subApiUrl = originalUrl.replace("/sub/", "/api/sub/")
            }
            val subResponse = apiService.getSubscriptionData(subApiUrl)
            if (!subResponse.isSuccessful || subResponse.body() == null) {
                if (showErrors) handleError(getString(R.string.error_connect))
                return FetchResult(false, false, null)
            }
            val subData = subResponse.body()!!

            requireActivity().runOnUiThread { updateUi(subData) }

            val gson = Gson()
            val subDataJson = gson.toJson(subData)
            sharedPref.edit { putString("last_sub_data", subDataJson) }

            val ipResponse = apiService.getPublicIp()
            if (!ipResponse.isSuccessful || ipResponse.body() == null) {
                if (showErrors) updateStatusBar(getString(R.string.ip_not_found), isError = true)
                return FetchResult(true, false, subData) // Data fetch was successful, but IP check failed
            }
            val publicIp = ipResponse.body()!!

            if (publicIp != subData.lastIp) {
                val token = originalUrl.split("/sub/").lastOrNull()
                if (token != null) {
                    val updateIpUrl = subApiUrl.split("/api/sub/")[0] + "/api/update_ip"
                    val updateRequest = UpdateIpRequest(token = token, ip = publicIp)
                    val updateResponse = apiService.updateIp(updateIpUrl, updateRequest)

                    if (updateResponse.isSuccessful) {
                        updateStatusBar(getString(R.string.ip_changed_from_to, subData.lastIp ?: "N/A", publicIp), isError = false)
                        return FetchResult(true, true, subData) // Needs countdown
                    } else {
                        val errorKey = if (updateResponse.code() == 409) R.string.ip_conflict_error else R.string.ip_update_fail
                        if (showErrors) updateStatusBar(getString(errorKey), isError = true)
                        return FetchResult(true, false, subData)
                    }
                }
            } else {
                updateStatusBar(getString(R.string.ip_no_change, publicIp), isError = false)
            }
            return FetchResult(true, false, subData) // Everything successful, no countdown needed

        } catch (e: Exception) {
            if (showErrors) handleError("خطا: ${e.message}")
            return FetchResult(false, false, null)
        } finally {
            requireActivity().runOnUiThread { binding.progressBar.isVisible = false }
        }
    }

    private fun updateStatusBar(message: String, isError: Boolean) {
        binding.statusBarText.text = message
        val color = if (isError) R.color.red else R.color.green // شما باید این رنگ‌ها را در colors.xml تعریف کنید
        binding.statusBarText.setTextColor(ContextCompat.getColor(requireContext(), color))
    }

    private fun updateUi(data: SubscriptionData) {
        lastSubscriptionData = data
        binding.resultCard.isVisible = true
        binding.usernameText.text = data.username
        val statusChip = binding.statusChip
        when (data.statusKey) {
            "table_status_active" -> {
                statusChip.text = getString(R.string.status_active)
                statusChip.setChipBackgroundColorResource(R.color.chip_color_green)
            }
            "table_status_expired" -> {
                statusChip.text = getString(R.string.status_expired)
                statusChip.setChipBackgroundColorResource(R.color.chip_color_red)
            }
            "limited" -> {
                statusChip.text = getString(R.string.status_limited)
                statusChip.setChipBackgroundColorResource(R.color.chip_color_orange)
            }
            else -> { // For "disabled" and other statuses
                statusChip.text = data.statusKey
                statusChip.setChipBackgroundColorResource(R.color.chip_color_gray)
            }
        }

        binding.timeText.text = if (data.isUnlimitedTime) {
            getString(R.string.unlimited)
        } else {
            // Using the string resource with placeholders for day and hour
            getString(R.string.time_format, data.remainingDays ?: 0, data.remainingHours ?: 0)
        }

        binding.volumeText.text = if (data.isUnlimitedVolume) {
            getString(R.string.unlimited)
        } else {
            val remainingGb = (data.allowedVolumeGb ?: 0.0) - (data.usedVolumeGb ?: 0.0)
            // Formatting the number and adding " GB"
            "${DecimalFormat("#.##").format(remainingGb)} GB"
        }
        binding.statusBarText.text = getString(R.string.success_status)
    }

    private fun handleError(errorMessage: String) {
        binding.resultCard.isVisible = false
        binding.statusBarText.text = ""
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().unregisterReceiver(vpnStateReceiver)
        _binding = null
    }
    private fun startCountdown() {
        // دکمه Fetch و منوی همبرگری را غیرفعال می‌کنیم
        binding.fetchButton.isEnabled = false
        (activity as? MainActivity)?.setDrawerEnabled(false)

        // لایه تایمر را نمایش می‌دهیم
        binding.timerOverlay.isVisible = true

        object : CountDownTimer(60000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                // فقط عدد ثانیه را در TextView وسط دایره نمایش می‌دهیم
                binding.timerTextOverlay.text = seconds.toString()
            }

            override fun onFinish() {
                // لایه تایمر را مخفی می‌کنیم
                binding.timerOverlay.isVisible = false
                // دکمه و منو را دوباره فعال می‌کنیم
                binding.fetchButton.isEnabled = true
                (activity as? MainActivity)?.setDrawerEnabled(true)

                // --- START: NEW CODE ADDED HERE ---
                // به صورت خودکار فرآیند اتصال را دوباره شروع می‌کنیم
                Toast.makeText(requireContext(), "زمان انتظار به پایان رسید، در حال اتصال...", Toast.LENGTH_SHORT).show()

                // وضعیت را به "متصل" تغییر داده و ذخیره می‌کنیم
                isConnected = true
                val sharedPref = activity?.getSharedPreferences("VexoDNSPrefs", Context.MODE_PRIVATE)
                sharedPref?.edit { putBoolean("is_connected_state", isConnected) }

                // تابع آپدیت دکمه را فراخوانی می‌کنیم تا اتصال برقرار شود
                updateButtonState()
                // --- END: NEW CODE ADDED HERE ---
            }
        }.start()
    }
}