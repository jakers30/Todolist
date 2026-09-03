package com.example.todolist.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.todolist.R
import com.example.todolist.TaskApp
import com.example.todolist.databinding.FragmentWelcomeBinding
import com.example.todolist.ui.common.navAnimOptions

/**
 * Fix #12: app entry point. Logged-in returning users are redirected straight to
 * their home by MainActivity (no welcome flash).
 */
class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back on the landing screen exits the app.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            requireActivity().finish()
        }

        // Fix #5: light/dark toggle at the top-right — switches the theme app-wide.
        binding.themeToggle.setOnClickListener {
            val themeManager = (requireActivity().application as TaskApp).container.themeManager
            themeManager.setDarkMode(!themeManager.isDarkMode())
            updateThemeIcon()
        }
        updateThemeIcon()

        binding.loginButton.setOnClickListener {
            findNavController().navigate(R.id.loginFragment, null, navAnimOptions(), null)
        }
        binding.exitButton.setOnClickListener {
            requireActivity().finish()
        }
    }

    /** Fix #5: the toggle icon reflects the mode it will switch TO. */
    private fun updateThemeIcon() {
        val dark = (requireActivity().application as TaskApp).container.themeManager.isDarkMode()
        binding.themeToggle.setImageResource(
            if (dark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode
        )
    }

    override fun onResume() {
        super.onResume()
        updateThemeIcon()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
