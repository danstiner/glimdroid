package com.danielstiner.glimdroid

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.danielstiner.glimdroid.databinding.ActivityMainBinding
import com.danielstiner.glimdroid.ui.login.LoginActivity
import com.danielstiner.glimdroid.ui.main.MainViewModel
import com.danielstiner.glimdroid.ui.main.MainViewModelFactory

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setTitle(R.string.title_live)

        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(this)
        )[MainViewModel::class.java]

        if (!viewModel.isAuthorized) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val drawerLayout = binding.drawerLayout

        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)

        viewModel.avatarUri.observe(this) {
            invalidateOptionsMenu()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_activity_actions, menu)

        val avatarUri = viewModel.avatarUri.value
        val userProfileItem = menu?.findItem(R.id.action_user_profile)!!

        if (avatarUri == null) {
            userProfileItem.setIcon(R.drawable.ic_account_circle_24dp)
        } else {
            Glide.with(this)
                .load(avatarUri)
                .circleCrop()
                .placeholder(R.drawable.ic_account_circle_24dp)
                .into(userProfileItem)
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                Log.d(TAG, "onOptionsItemSelected: home")
//                if (drawerLayout.isDrawerOpen(drawerList)) {
//                    drawerLayout.closeDrawer(drawerList)
//                } else {
//                    drawerLayout.openDrawer(drawerList)
//                }
                true
            }
            R.id.action_user_profile -> {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        USER_PROFILE_URI
                    )
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val USER_PROFILE_URI =
            Uri.parse(BuildConfig.GLIMESH_BASE_URL).buildUpon()
                .appendEncodedPath("users/settings/profile")
                .build()
    }
}

private fun RequestBuilder<Drawable>.into(menuItem: MenuItem) {
    into(object : CustomTarget<Drawable>() {
        override fun onResourceReady(
            resource: Drawable,
            transition: Transition<in Drawable?>?
        ) {
            menuItem.icon = resource
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            menuItem.icon = placeholder
        }
    })
}
