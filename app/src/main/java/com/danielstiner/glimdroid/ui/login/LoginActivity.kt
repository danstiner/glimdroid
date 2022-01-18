package com.danielstiner.glimdroid.ui.login

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.danielstiner.glimdroid.MainActivity
import com.danielstiner.glimdroid.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private val TAG = "LoginActivity"
    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val buttonLogin = binding.buttonLogin

        loginViewModel = ViewModelProvider(
            this,
            LoginViewModelFactory(
                applicationContext,
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, TokenActivity::class.java),
                    PendingIntent.FLAG_MUTABLE
                )
            )
        )[LoginViewModel::class.java]

        if (loginViewModel.isAuthorized) {
            Log.i(TAG, "User is already authorized, jumping to main activity")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            if (loginResult.error != null) {
                showLoginFailed(loginResult.error)
            }
            if (loginResult.success != null) {
                // TODO
            }
//            setResult(Activity.RESULT_OK)
//
//            //Complete and destroy login activity once successful
//            finish()
        })

        buttonLogin.setOnClickListener {
            loginViewModel.login()
        }
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}
