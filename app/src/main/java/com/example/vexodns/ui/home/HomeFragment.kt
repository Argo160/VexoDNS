package com.example.vexodns.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vexodns.R
import com.example.vexodns.data.SubscriptionData
import com.example.vexodns.databinding.FragmentHomeBinding
import com.example.vexodns.network.ApiService
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.DecimalFormat

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.fetchButton.setOnClickListener {
            fetchSubscriptionData()
        }

        return root
    }

    private fun fetchSubscriptionData() {
        // Read saved link from SharedPreferences
        val sharedPref = activity?.getSharedPreferences("VexoDNSPrefs", Context.MODE_PRIVATE)
        var url = sharedPref?.getString("subscription_link", null)

        if (url.isNullOrBlank()) {
            Toast.makeText(context, "ابتدا لینک اشتراک را از منو اضافه کنید", Toast.LENGTH_LONG).show()
            return
        }

        // Prepare API URL
        if ("/sub/" in url && "/api/sub/" !in url) {
            url = url.replace("/sub/", "/api/sub/")
        }

        // Show loading
        binding.progressBar.isVisible = true
        binding.resultTable.isVisible = false
        binding.statusBarText.text = "در حال اتصال..."

        // Create Retrofit instance
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/") // This is a placeholder and will be overridden
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val apiService = retrofit.create(ApiService::class.java)

        // Launch a coroutine for the network request
        lifecycleScope.launch {
            try {
                val response = apiService.getSubscriptionData(url)
                if (response.isSuccessful && response.body() != null) {
                    updateUi(response.body()!!)
                } else {
                    handleError("خطا در دریافت اطلاعات: ${response.code()}")
                }
            } catch (e: Exception) {
                handleError("خطا در اتصال: ${e.message}")
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun updateUi(data: SubscriptionData) {
        binding.resultTable.isVisible = true
        binding.usernameText.text = data.username
        binding.statusText.text = when (data.statusKey) {
            "table_status_active" -> "فعال"
            "sub_status_disabled" -> "غیرفعال"
            "limited" -> "محدود شده"
            "table_status_expired" -> "منقضی شده"
            else -> data.statusKey
        }

        binding.timeText.text = if (data.isUnlimitedTime) {
            "نامحدود"
        } else {
            "${data.remainingDays ?: 0} روز و ${data.remainingHours ?: 0} ساعت"
        }

        binding.volumeText.text = if (data.isUnlimitedVolume) {
            "نامحدود"
        } else {
            val remainingGb = (data.allowedVolumeGb ?: 0.0) - (data.usedVolumeGb ?: 0.0)
            "${DecimalFormat("#.##").format(remainingGb)} GB"
        }
        binding.ipText.text = data.lastIp ?: "N/A"
        binding.statusBarText.text = "اطلاعات با موفقیت دریافت شد."
    }

    private fun handleError(errorMessage: String) {
        binding.resultTable.isVisible = false
        binding.statusBarText.text = ""
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}