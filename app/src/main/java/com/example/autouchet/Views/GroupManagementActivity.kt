package com.example.autouchet.Views

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
            try {
                val groupId = SharedPrefsHelper.getGroupId(this@GroupManagementActivity)
                if (groupId == null) {
                    withContext(Dispatchers.Main) {
                        binding.inviteCodeTextView.text = "Нет группы"
                        binding.membersTextView.text = "Создайте или присоединитесь к группе"
                    }
                    return@launch
                }

                val document = firestore.collection("carGroups").document(groupId).get().await()

                if (document.exists()) {
                    val inviteCode = document.getString("inviteCode") ?: ""
                    val ownerUid = document.getString("ownerUid") ?: ""
                    val members = document.get("members") as? List<String> ?: emptyList()

                    // Загружаем имена участников
                    val memberNames = mutableListOf<String>()
                    for (uid in members) {
                        try {
                            val userDoc = firestore.collection("users").document(uid).get().await()
                            val name = userDoc.getString("displayName")
                                ?: userDoc.getString("email")
                                ?: uid.take(8) + "..."
                            val isOwner = uid == ownerUid
                            val displayName = if (isOwner) "👑 $name (Владелец)" else "👤 $name"
                            memberNames.add(displayName)
                        } catch (e: Exception) {
                            memberNames.add("👤 ${uid.take(8)}...")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        binding.inviteCodeTextView.text = inviteCode.ifEmpty { "Код не найден" }
                        binding.membersTextView.text = if (memberNames.isNotEmpty()) {
                            "Участники (${members.size}):\n\n" + memberNames.joinToString("\n")
                        } else {
                            "Нет участников"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.inviteCodeTextView.text = "Группа не найдена"
                        binding.membersTextView.text = "Ошибка загрузки"
                    }
                }
            } catch (e: Exception) {
                Log.e("GroupManagement", "Error loading group info: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.membersTextView.text = "Ошибка: ${e.message}"
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.shareCodeButton.setOnClickListener {
            val code = binding.inviteCodeTextView.text.toString()
            if (code.isNotEmpty() && code != "Нет группы" && code != "Код не найден") {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Присоединяйтесь к моему автомобилю в АвтоУчёт! Код: $code")
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(shareIntent, "Поделиться кодом"))
            } else {
                Toast.makeText(this, "Сначала создайте группу", Toast.LENGTH_SHORT).show()
            }
        }

        binding.joinGroupButton.setOnClickListener {
            val code = binding.joinCodeEditText.text.toString().trim().uppercase()
            if (code.isEmpty()) {
                Toast.makeText(this, "Введите код приглашения", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (code.length != 8) {
                Toast.makeText(this, "Код должен быть 8 символов", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            joinGroup(code)
        }
    }

    private fun joinGroup(code: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = firebaseController.joinCarGroup(code)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { groupId ->
                        SharedPrefsHelper.setGroupId(this@GroupManagementActivity, groupId)
                        SharedPrefsHelper.setSyncEnabled(this@GroupManagementActivity, true)
                        Toast.makeText(this@GroupManagementActivity, "✅ Вы присоединились к группе!", Toast.LENGTH_SHORT).show()
                        loadGroupInfo()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@GroupManagementActivity, "❌ ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}