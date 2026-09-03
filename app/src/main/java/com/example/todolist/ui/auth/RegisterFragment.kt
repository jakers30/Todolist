package com.example.todolist.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.example.todolist.R
import com.example.todolist.TaskApp
import com.example.todolist.data.model.Batch
import com.example.todolist.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels {
        viewModelFactory {
            initializer {
                AuthViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TaskApp).container)
            }
        }
    }

    private var batches: List<Batch> = emptyList()
    private var selectedBatchId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadBatches()
        viewModel.batches.observe(viewLifecycleOwner) { list ->
            batches = list
            // Fix #2: "No Batch" is offered as the first option (maps to batch id 0).
            binding.batchDropdown.setSimpleItems(arrayOf("No Batch") + list.map { it.name }.toTypedArray())
            binding.batchDropdown.setText("", false)
        }
        binding.batchDropdown.setOnItemClickListener { _, _, position, _ ->
            // Fix #2: position 0 = "No Batch"; anything else maps to a real batch.
            selectedBatchId = if (position == 0) 0L else batches.getOrNull(position - 1)?.id ?: -1L
        }

        binding.registerButton.setOnClickListener {
            viewModel.register(
                binding.username.text?.toString().orEmpty(),
                binding.password.text?.toString().orEmpty(),
                binding.confirmPassword.text?.toString().orEmpty(),
                selectedBatchId
            )
        }
        binding.backToLogin.setOnClickListener {
            findNavController().popBackStack()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.loadingOverlay.root.isVisible = it
            binding.registerButton.isEnabled = !it
        }
        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                com.google.android.material.snackbar.Snackbar.make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
        viewModel.onRegistered.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { findNavController().popBackStack() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
