package com.sesac.speechapp.ui.profile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sesac.speechapp.databinding.ActivitySettingBinding

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

        // 회원 탈퇴
        binding.btnDeleteAccount.setOnClickListener {
            // TODO: P2-11 회원 탈퇴 API 호출
            Toast.makeText(this, "회원 탈퇴 (미구현)", Toast.LENGTH_SHORT).show()
        }
    }
}
