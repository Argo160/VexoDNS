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
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.text.DecimalFormat
import android.os.CountDownTimer
import com.example.vexodns.MainActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // یک نمونه از ApiService برای استفاده در کل کلاس
    private val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://example.com/") // Placeholder
            // ScalarsConverterFactory برای خواندن پاسخ‌های متنی ساده مثل IP لازم است
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.fetchButton.setOnClickListener { fetchSubscriptionData() }
        return binding.root
    }

    private fun fetchSubscriptionData() {
        val sharedPref = activity?.getSharedPreferences("VexoDNSPrefs", Context.MODE_PRIVATE)
        var originalUrl = sharedPref?.getString("subscription_link", null)

        if (originalUrl.isNullOrBlank()) {
            Toast.makeText(context, getString(R.string.warning_text), Toast.LENGTH_LONG).show()
            return
        }

        binding.progressBar.isVisible = true
        binding.resultCard.isVisible = false
        binding.statusBarText.text = getString(R.string.connecting_status)

        lifecycleScope.launch {
            try {
                // --- مرحله ۱: دریافت اطلاعات اشتراک ---
                var subApiUrl = originalUrl
                if ("/sub/" in originalUrl && "/api/sub/" !in originalUrl) {
                    subApiUrl = originalUrl.replace("/sub/", "/api/sub/")
                }
                val subResponse = apiService.getSubscriptionData(subApiUrl)
                if (!subResponse.isSuccessful || subResponse.body() == null) {
                    handleError(getString(R.string.error_connect))
                    return@launch
                }
                val subData = subResponse.body()!!
                updateUi(subData) // نمایش اولیه اطلاعات

                // --- مرحله ۲: دریافت IP عمومی ---
                val ipResponse = apiService.getPublicIp()
                if (!ipResponse.isSuccessful || ipResponse.body() == null) {
                    updateStatusBar(getString(R.string.ip_not_found), isError = true)
                    return@launch
                }
                val publicIp = ipResponse.body()!!

                // --- مرحله ۳: مقایسه و آپدیت IP ---
                if (publicIp != subData.lastIp) {
                    val token = originalUrl.split("/sub/").lastOrNull()
                    if (token != null) {
                        val updateIpUrl = subApiUrl.split("/api/sub/")[0] + "/api/update_ip"
                        val updateRequest = UpdateIpRequest(token = token, ip = publicIp)
                        val updateResponse = apiService.updateIp(updateIpUrl, updateRequest)

                        if (updateResponse.isSuccessful) {
                            // آپدیت UI با IP جدید
                            binding.ipText.text = publicIp
                            updateStatusBar(getString(R.string.ip_changed_from_to, subData.lastIp ?: "N/A", publicIp), isError = false)
                            startCountdown()
                        } else {
                            // مدیریت خطاهایی مثل IP Conflict
                            if (updateResponse.code() == 409) {
                                updateStatusBar(getString(R.string.ip_conflict_error), isError = true)
                            } else {
                                updateStatusBar(getString(R.string.ip_update_fail), isError = true)
                            }
                        }
                    }
                } else {
                    updateStatusBar(getString(R.string.ip_no_change, publicIp), isError = false)
                }

            } catch (e: Exception) {
                handleError("خطا: ${e.message}")
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun updateStatusBar(message: String, isError: Boolean) {
        binding.statusBarText.text = message
        val color = if (isError) R.color.red else R.color.green // شما باید این رنگ‌ها را در colors.xml تعریف کنید
        binding.statusBarText.setTextColor(ContextCompat.getColor(requireContext(), color))
    }

    private fun updateUi(data: SubscriptionData) {
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
        binding.ipText.text = data.lastIp ?: "N/A"
        binding.statusBarText.text = getString(R.string.success_status)
    }

    private fun handleError(errorMessage: String) {
        binding.resultCard.isVisible = false
        binding.statusBarText.text = ""
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
            }
        }.start()
    }
}