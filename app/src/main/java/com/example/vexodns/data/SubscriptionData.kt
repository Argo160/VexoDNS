package com.example.vexodns.data // این خط باید با نام پکیج شما یکی باشد

import com.google.gson.annotations.SerializedName

data class SubscriptionData(
    val username: String,
    @SerializedName("status_key")
    val statusKey: String,
    @SerializedName("is_unlimited_time")
    val isUnlimitedTime: Boolean,
    @SerializedName("remaining_days")
    val remainingDays: Int?,
    @SerializedName("remaining_hours")
    val remainingHours: Int?,
    @SerializedName("is_unlimited_volume")
    val isUnlimitedVolume: Boolean,
    @SerializedName("allowed_volume_gb")
    val allowedVolumeGb: Double?,
    @SerializedName("used_volume_gb")
    val usedVolumeGb: Double?,
    @SerializedName("dou_ip1")
    val douIp1: String?,
    @SerializedName("last_ip")
    val lastIp: String?
)