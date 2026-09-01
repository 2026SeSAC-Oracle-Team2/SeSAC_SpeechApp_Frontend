package com.sesac.speechapp.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sesac.speechapp.databinding.ActivityPermissionOnboardingBinding

/**
 * P3-22: 녹음 권한 온보딩 UX
 * - 위치: 스플래시 후 · 로그인 전 · 최초 1회
 * - SharedPreferences "onboarding_completed" 플래그 관리
 * - 거부 시: 녹음 컨텐츠 진입 시 재노출 + 설정 이동 안내
 */
class PermissionOnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionOnboardingBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            markCompleted()
            finish()
        } else {
            // 거부 시 설정 이동 안내
            binding.tvDescription.text = "권한이 거부되었습니다.\n설정에서 RECORD_AUDIO 권한을 허용해주세요."
            binding.btnAgree.text = "설정으로 이동"
            binding.btnAgree.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvDescription.text =
            "발화 연습을 위해 마이크 녹음 권한이 필요합니다.\n\n" +
            "녹음된 음성은 서버로 업로드되어 AI가 평가하고 피드백을 제공합니다."

        binding.btnAgree.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        binding.btnSkip.setOnClickListener {
            // 거부 상태로 저장, 다음 진입 시 재노출
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // 설정에서 돌아왔을 때 권한 다시 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            markCompleted()
            finish()
        }
    }

    private fun markCompleted() {
        getSharedPreferences("speechapp_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()
    }

    companion object {
        fun shouldShow(context: Context): Boolean {
            val prefs = context.getSharedPreferences("speechapp_prefs", Context.MODE_PRIVATE)
            val completed = prefs.getBoolean("onboarding_completed", false)
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            return !completed && !hasPermission
        }
    }
}
