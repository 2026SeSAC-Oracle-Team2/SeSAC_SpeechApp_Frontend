package com.sesac.speech.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sesac.speech.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 설정 버튼 → SettingActivity
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingActivity::class.java))
        }

        // 로그아웃 버튼 (Stub)
        binding.btnLogout.setOnClickListener {
            // TODO: P2-09 Firebase Auth 로그아웃
            //  AuthRepository.signOut()
            //  → LoginActivity로 이동
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
