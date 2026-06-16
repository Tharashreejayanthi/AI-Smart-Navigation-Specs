package com.innovista.smartglasses.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.innovista.smartglasses.R
import com.innovista.smartglasses.databinding.FragmentSosSetupBinding
import com.innovista.smartglasses.services.VoiceCommand
import com.innovista.smartglasses.services.VoiceEngine
import com.innovista.smartglasses.services.VoiceEngineCallback

data class EmergencyContact(val name: String, val phone: String)

class SOSSetupFragment : Fragment(), VoiceEngineCallback {

    private var _binding: FragmentSosSetupBinding? = null
    private val binding get() = _binding!!

    private lateinit var voiceEngine: VoiceEngine
    private val contacts = mutableListOf<EmergencyContact>()
    private lateinit var adapter: ContactAdapter

    private var voiceSetupStep = 0
    private var tempName = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSosSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        voiceEngine = VoiceEngine.getInstance(requireContext())
        loadContacts()

        adapter = ContactAdapter(contacts) { contact ->
            contacts.remove(contact)
            saveContacts()
            adapter.notifyDataSetChanged()
        }

        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter

        binding.btnAddContact.setOnClickListener {
            val name = binding.etName.text.toString()
            val phone = binding.etPhone.text.toString()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                addContact(EmergencyContact(name, phone))
                binding.etName.text.clear()
                binding.etPhone.text.clear()
            }
        }

        binding.btnVoiceSetup.setOnClickListener {
            startVoiceSetup()
        }
    }

    private fun startVoiceSetup() {
        voiceSetupStep = 1
        voiceEngine.speak("Please say your first emergency contact name")
        voiceEngine.startListening(this)
    }

    private fun addContact(contact: EmergencyContact) {
        if (contacts.size < 3) {
            contacts.add(contact)
            saveContacts()
            adapter.notifyDataSetChanged()
        } else {
            voiceEngine.speak("Maximum 3 contacts allowed")
        }
    }

    private fun saveContacts() {
        val prefs = requireContext().getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(contacts)
        prefs.edit().putString("emergency_contacts", json).apply()
    }

    private fun loadContacts() {
        val prefs = requireContext().getSharedPreferences("SmartGlassesPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("emergency_contacts", null)
        if (json != null) {
            val type = object : TypeToken<List<EmergencyContact>>() {}.type
            val loaded: List<EmergencyContact> = Gson().fromJson(json, type)
            contacts.clear()
            contacts.addAll(loaded)
        }
    }

    override fun onCommandReceived(text: String, command: VoiceCommand) {
        when (voiceSetupStep) {
            1 -> {
                tempName = text
                voiceSetupStep = 2
                voiceEngine.speak("Please say the phone number for $tempName")
                voiceEngine.startListening(this)
            }
            2 -> {
                val phone = text.replace(" ", "")
                addContact(EmergencyContact(tempName, phone))
                if (contacts.size < 3) {
                    voiceSetupStep = 1
                    voiceEngine.speak("Contact added. Please say your next emergency contact name, or say stop to finish.")
                    voiceEngine.startListening(this)
                } else {
                    voiceSetupStep = 0
                    voiceEngine.speak("All 3 contacts saved. Setup complete.")
                }
            }
        }
        
        if (command == VoiceCommand.STOP) {
            voiceSetupStep = 0
            voiceEngine.speak("Voice setup stopped")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ContactAdapter(
        private val items: List<EmergencyContact>,
        private val onDelete: (EmergencyContact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvInfo: TextView = view.findViewById(android.R.id.text1)
            init {
                view.setOnClickListener { onDelete(items[adapterPosition]) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = items[position]
            holder.tvInfo.text = "${contact.name}: ${contact.phone}"
            holder.tvInfo.setTextColor(android.graphics.Color.WHITE)
        }

        override fun getItemCount() = items.size
    }
}
