package com.mypro.myocr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.mypro.myocr.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        initView()
    }

    private fun initView() {
        binding.apply {
            btnScan.setOnClickListener {
                resultScanLauncher.launch(Intent(this@MainActivity, ScanActivity::class.java))
            }
        }
    }

    private val resultScanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                val text = intent?.getStringExtra(ScanActivity.EXTRA_TEXT)
                binding.tvResult.text = text
            }
        }
}