package com.example.autouchet.Views

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autouchet.Controllers.FirebaseController
import com.example.autouchet.Utils.SharedPrefsHelper
import com.example.autouchet.databinding.ActivityGroupManagementBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GroupManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupManagementBinding
    private lateinit var firebaseController: FirebaseController
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firebaseController = FirebaseController()
        loadGroupInfo()
        setupClickListeners()
    }

    private fun loadGroupInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            val groupId = SharedPrefsHelper.getGroupId(this@GroupManagementActivity)
            if (groupId != null) {
                try {
                    val document = firestore.collection("carGroups").document(groupId).get().await()
                    val inviteCode = document.getString("inviteCode") ?: groupId.take(8)
                    val members = document.get("members") as? Map<String, String> ?: emptyMap()
                    withContext(Dispatchers.Main) {
                        binding.inviteCodeTextView.text = inviteCode
                        val membersText = members.entries.joinToString("\n") { (uid, role) -> "${if (role == "owner") "👑" else "👤"} ${uid.take(8)}... (${if (role == "owner") "Владелец" else "Участник"})" }
                        binding.membersTextView.text = membersText
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.inviteCodeTextView.text = groupId.take(8).uppercase()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.shareCodeButton.setOnClickListener {
            val code = binding.inviteCodeTextView.text.toString()
            val shareIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "Присоединяйтесь к моему автомобилю в приложении АвтоУчёт! Код приглашения: $code"); type = "text/plain" }
            startActivity(Intent.createChooser(shareIntent, "Поделиться кодом"))
        }
        binding.joinGroupButton.setOnClickListener {
            val code = binding.joinCodeEditText.text.toString().trim()
            if (code.isEmpty()) { Toast.makeText(this, "Введите код приглашения", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            joinGroup(code)
        }
    }

    private fun joinGroup(code: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = firebaseController.joinCarGroup(code)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { groupId -> SharedPrefsHelper.setGroupId(this@GroupManagementActivity, groupId); Toast.makeText(this@GroupManagementActivity, "Вы присоединились к группе!", Toast.LENGTH_SHORT).show(); loadGroupInfo() },
                    onFailure = { Toast.makeText(this@GroupManagementActivity, "Ошибка: ${it.message}", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}