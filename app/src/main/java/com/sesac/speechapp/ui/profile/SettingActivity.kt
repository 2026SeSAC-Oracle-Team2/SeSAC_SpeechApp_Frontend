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

        // 알림 시간
        binding.tvReminderTime.setOnClickListener {
            // TODO: P2-11 TimePickerDialog
            Toast.makeText(this, "시간 선택 (미구현)", Toast.LENGTH_SHORT).show()
        }

        // 계정 이메일 표시
        val savedEmail = TokenManager(this).getUserEmail().orEmpty()
        if (savedEmail.isNotEmpty()) {
            binding.tvEmail.text = savedEmail
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