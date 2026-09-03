package com.example.todolist.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.example.todolist.R
import com.example.todolist.TaskApp
import com.example.todolist.data.model.Role
import com.example.todolist.databinding.FragmentLoginBinding
import com.example.todolist.ui.common.Event
import com.example.todolist.ui.common.navAnimOptions

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                AuthViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fix #3: back button returns to the Welcome screen.
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        // Same light/dark toggle as the Welcome screen — switches the theme app-wide.
        binding.themeToggle.setOnClickListener {
            val themeManager = (requireActivity().application as TaskApp).container.themeManager
            themeManager.setDarkMode(!themeManager.isDarkMode())
            updateThemeIcon()
        }
        updateThemeIcon()

        // Fix #3: system back also returns to Welcome instead of exiting.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (!findNavController().popBackStack()) {
                requireActivity().finish()
            }
        }

        binding.loginButton.setOnClickListener {
            viewModel.login(
                binding.username.text?.toString().orEmpty(),
                binding.password.text?.toString().orEmpty()
            )
        }
        binding.forgotButton.setOnClickListener {
            findNavController().navigate(R.id.forgotPasswordFragment, null, navAnimOptions(), null)
        }
        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.registerFragment, null, navAnimOptions(), null)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.loadingOverlay.root.isVisible = it
            binding.loginButton.isEnabled = !it
        }
        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                com.google.android.material.snackbar.Snackbar.make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
        viewModel.onLoggedIn.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { role ->
                if (role == Role.ADMIN) {
                    (activity as? com.example.todolist.MainActivity)?.navigateTo(R.id.adminTasksFragment, clearBackStack = true)
                } else {
                    (activity as? com.example.todolist.MainActivity)?.navigateTo(R.id.userBoardFragment, clearBackStack = true)
                }
            }
        }
    }

    /** The toggle icon reflects the mode it will switch TO. */
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
