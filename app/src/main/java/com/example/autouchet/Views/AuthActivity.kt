package com.example.autouchet.Views

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.FirebaseController
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityAuthBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthBinding
    private lateinit var firebaseController: FirebaseController
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseController = FirebaseController()

        if (firebaseController.isLoggedIn() && SharedPrefsHelper.isLoggedIn(this)) {
            startMainActivity()
            return
        }

        setupUI()
    }

    private fun setupUI() {
        binding.switchModeTextView.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUIMode()
        }

        binding.forgotPasswordTextView.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Введите email для восстановления пароля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            resetPassword(email)
        }

        binding.authButton.setOnClickListener {
            if (isLoginMode) {
                loginUser()
            } else {
                registerUser()
            }
        }
    }

    private fun updateUIMode() {
        if (isLoginMode) {
            binding.authButton.text = "ВОЙТИ"
            binding.switchModeTextView.text = "Нет аккаунта? Зарегистрироваться"
            binding.confirmPasswordLayout.visibility = View.GONE
            binding.forgotPasswordTextView.visibility = View.VISIBLE
        } else {
            binding.authButton.text = "ЗАРЕГИСТРИРОВАТЬСЯ"
            binding.switchModeTextView.text = "Уже есть аккаунт? Войти"
            binding.confirmPasswordLayout.visibility = View.VISIBLE
            binding.forgotPasswordTextView.visibility = View.GONE
        }
    }

    private fun loginUser() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            val result = firebaseController.loginUser(email, password)
            withContext(Dispatchers.Main) {
                showLoading(false)
                result.fold(
                    onSuccess = { uid ->
                        SharedPrefsHelper.setUserUid(this@AuthActivity, uid)
                        SharedPrefsHelper.setLoggedIn(this@AuthActivity, true)
                        startMainActivity()
                    },
                    onFailure = {
                        Toast.makeText(this@AuthActivity, it.message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun registerUser() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Пароль должен содержать минимум 6 символов", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            val result = firebaseController.registerUser(email, password)
            withContext(Dispatchers.Main) {
                showLoading(false)
                result.fold(
                    onSuccess = { uid ->
                        SharedPrefsHelper.setUserUid(this@AuthActivity, uid)
                        SharedPrefsHelper.setLoggedIn(this@AuthActivity, true)
                        startMainActivity()
                    },
                    onFailure = {
                        Toast.makeText(this@AuthActivity, it.message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun resetPassword(email: String) {
        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = firebaseController.resetPassword(email)
            withContext(Dispatchers.Main) {
                showLoading(false)
                result.fold(
                    onSuccess = {
                        Toast.makeText(this@AuthActivity, "Письмо для восстановления пароля отправлено на $email", Toast.LENGTH_LONG).show()
                    },
                    onFailure = {
                        Toast.makeText(this@AuthActivity, it.message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.authButton.isEnabled = !show
    }
}