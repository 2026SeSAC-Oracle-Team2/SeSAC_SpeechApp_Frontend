package com.sesac.speechapp.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sesac.speechapp.data.local.TokenManager
import com.sesac.speechapp.data.repository.AuthRepository
import com.sesac.speechapp.databinding.ActivitySettingBinding
import com.sesac.speechapp.ui.login.LoginActivity
import kotlinx.coroutines.launch

/**
 * 설정 화면 — D-8-C1 시안 settings.tsx 재작성.
 *
 * - 계정 카드(원형 "덕" + 이름/이메일) + 알림 섹션(토글 2개 + 연습 시간 행) +
 *   접근성 섹션(토글 3개 — 시안 신설) + 계정 섹션(로그아웃/회원 탈퇴)
 * - ⚠ 접근성 토글 3개(switchA11yBigText/switchA11yVoice/switchA11ySlow)는 시안 신설 요소로
 *   기존 기획에 기능 정의가 없다 → UI만 추가하고 동작 연결은 하지 않음(미연결 저장 방지 —
 *   지시문 §4-11 "저장은 하되 기능 미연결 금지"). 리스너 미등록 + 보고서 보류 항목 표기.
 * - 로그아웃 행 신설(btnLogoutSetting) — 기존 ProfileFragment 로직과 동일 흐름.
 * - tvAccountName: TokenManager 이메일로 근사 (이름 API 별도 없음 — 보고서 리스크 표기).
 */
class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // 푸시 알림 스위치
        binding.switchPush.setOnCheckedChangeListener { _, isChecked ->
            // TODO: P2-11 SharedPreferences 저장, 서버 동기화
            Toast.makeText(this, "푸시 알림 ${if (isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        // 연습 알림 스위치
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            // TODO: P2-11 AlarmManager 설정/해제
            Toast.makeText(this, "연습 알림 ${if (isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        // ⚠ 접근성 토글 3개 — 시안 신설, 동작 미연결 (기획 확인 전 — 보류 항목)
        // switchA11yBigText / switchA11yVoice / switchA11ySlow: 리스너 등록 안 함

        // 알림 시간
        binding.tvReminderTime.setOnClickListener {
            // TODO: P2-11 TimePickerDialog
            Toast.makeText(this, "시간 선택 (미구현)", Toast.LENGTH_SHORT).show()
        }

        // 계정 이메일 표시 (시안 이름 행 — 이메일로 근사)
        val savedEmail = TokenManager(this).getUserEmail().orEmpty()
        if (savedEmail.isNotEmpty()) {
            binding.tvEmail.text = savedEmail
            binding.tvAccountName.text = savedEmail.substringBefore("@")
        }

        // 로그아웃 행 (시안 신설 — ProfileFragment와 동일 흐름)
        binding.btnLogoutSetting.setOnClickListener {
            lifecycleScope.launch {
                AuthRepository(applicationContext).logout()
                val intent = Intent(this@SettingActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }

        // 회원 탈퇴
        binding.btnDeleteAccount.setOnClickListener {
            showWithdrawConfirmDialog()
        }
    }

    private fun showWithdrawConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("회원탈퇴")
            .setMessage("정말 탈퇴하시겠습니까?\n모든 데이터가 삭제되며 복구할 수 없습니다.")
            .setPositiveButton("예") { _, _ -> withdraw() }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun withdraw() {
        // 재확인 방지: 버튼 비활성화
        binding.btnDeleteAccount.isEnabled = false

        lifecycleScope.launch {
            val result = AuthRepository(applicationContext).withdraw()

            result.fold(
                onSuccess = {
                    Toast.makeText(this@SettingActivity, "회원탈퇴가 완료되었습니다", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@SettingActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                },
                onFailure = { e ->
                    binding.btnDeleteAccount.isEnabled = true
                    Toast.makeText(
                        this@SettingActivity,
                        e.message ?: "회원탈퇴에 실패했습니다",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}