package com.nkdevs.chaarvisdonnebiryani

import MenuAdapter
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID


class AdminPage : AppCompatActivity() {

    private lateinit var buttonAddItem: ImageButton
    private lateinit var buttonDoneHome: Button
    private lateinit var scrollViewMenu: ScrollView
    private lateinit var bannerImageView: ImageView
    private lateinit var buttonLocation: ImageButton
    private lateinit var database: DatabaseReference
    private lateinit var buttonEditOffer: Button
    private lateinit var offerStatusRef: DatabaseReference

    //Menu related code below
    private val PICK_IMAGE_REQUEST = 1
    private var selectedImageUri: Uri? = null
    private lateinit var imagePreview: ImageView
    private lateinit var recyclerViewMenu: RecyclerView
    private lateinit var adapter: MenuAdapter
    private val menuItems = mutableListOf<android.view.MenuItem>()
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var originalMenuItems = mutableListOf<android.view.MenuItem>()
    private lateinit var sharedPreferences: SharedPreferences
    //Menu related code above


    private var selectedOfferStatus: String? = null
    private var selectedImageUri: Uri? = null

    private val PICK_BANNER_IMAGE_REQUEST = 101 // Request code for banner image

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_page)

        buttonAddItem = findViewById(R.id.buttonAddItem)
        buttonDoneHome = findViewById(R.id.buttonDoneHome)
        scrollViewMenu = findViewById(R.id.scrollViewMenu)
        val bannerCardView: CardView = findViewById(R.id.bannerCardView)
        bannerImageView = findViewById(R.id.bannerImageView)
        buttonLocation = findViewById(R.id.buttonLocation)
        buttonEditOffer = findViewById(R.id.buttonEditOffer)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.title = "Admin Dashboard"

        // Handle "Add Item" button click
        buttonAddItem.setOnClickListener {

        }

        // Handle "Done" button click
        buttonDoneHome.setOnClickListener {
            updateRestaurantStatus()
            uploadOfferStatusToServer()


            if (selectedImageUri != null) {
                uploadOfferImageToServer(selectedImageUri!!)
            }
        }

        offerStatusRef = FirebaseDatabase.getInstance().getReference("offer_status")
        setupOfferStatusSpinner()

        buttonEditOffer.setOnClickListener {
            val pickBannerImageIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickBannerImageIntent, PICK_BANNER_IMAGE_REQUEST)
        }

        buttonLocation.setOnClickListener {
            showLocationPopup()
        }

        val buttonPhone: ImageButton = findViewById(R.id.buttonPhone)
        buttonPhone.setOnClickListener {
            // Add logic to open the phone number popup
            showPhoneNumberPopup()
        }

        database = FirebaseDatabase.getInstance().reference

        val spinnerStatus = findViewById<Spinner>(R.id.spinnerStatus)

        setupStatusSpinner()
        fetchOfferStatus()
        loadBannerImage()

// Listen for changes in the status from Firebase
        database.child("restaurantStatus").get().addOnSuccessListener {
            val currentStatus = it.value.toString()
            val statusArray = resources.getStringArray(R.array.status_array)
            val index = statusArray.indexOf(currentStatus)
            if (index != -1) {
                spinnerStatus.setSelection(index)
            }
        }


        //Menu related code below
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        sharedPreferences = getSharedPreferences("MenuData", MODE_PRIVATE)

        recyclerViewMenu = findViewById(R.id.recyclerViewMenu)
        recyclerViewMenu.layoutManager = LinearLayoutManager(this)

        adapter = MenuAdapter(this, menuItems)
        recyclerViewMenu.adapter = adapter

        val buttonAddItem: ImageView = findViewById(R.id.buttonAddItem)
        buttonAddItem.setOnClickListener {
            showAddItemPopup()
        }

        val buttonDoneHome: Button = findViewById(R.id.buttonDoneHome)
        buttonDoneHome.setOnClickListener {
            handleMenuChanges(buttonDoneHome)
        }

        showSplashScreen()
        fetchMenuItems()
        //Menu related code above

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            when (requestCode) {
                PICK_BANNER_IMAGE_REQUEST -> {
                    selectedImageUri = imageUri // Store the selected URI
                    val bannerImage = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                    bannerImageView.setImageBitmap(bannerImage) // Display the selected banner image
                }
            }
        }
    }

    private fun loadBannerImage() {
        // Reference to the Firebase Storage where the banner image is stored
        val storageReference = FirebaseStorage.getInstance().reference.child("offers/banner_image.jpg")

        // Fetch the download URL of the image
        storageReference.downloadUrl.addOnSuccessListener { uri ->
            // Use Glide (or Picasso) to load the image from the URI into the ImageView
            Glide.with(this)
                .load(uri)
                .into(bannerImageView) // Your ImageView reference
        }.addOnFailureListener {
            // Handle any errors (e.g., image not found)
            Toast.makeText(this, "Failed to load banner image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressImage(imageUri: Uri): Bitmap? {
        return try {
            // Load the image into a Bitmap using Glide
            Glide.with(this)
                .asBitmap()
                .load(imageUri)
                .submit()
                .get() // This will block until the image is loaded
        } catch (e: Exception) {
            Log.e("ImageCompression", "Error compressing image: ${e.message}")
            null
        }
    }

    private fun uploadOfferImageToServer(imageUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val compressedBitmap = compressImage(imageUri)

            compressedBitmap?.let { bitmap ->
                val storageReference = FirebaseStorage.getInstance().reference.child("offers/banner_image.jpg")

                // Convert the Bitmap to ByteArray for upload
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                val data = byteArrayOutputStream.toByteArray()

                // Upload the ByteArray to Firebase Storage
                val uploadTask = storageReference.putBytes(data)

                uploadTask.addOnSuccessListener {
                    storageReference.downloadUrl.addOnSuccessListener { downloadUri ->
                        Log.d("Upload", "Image uploaded successfully: $downloadUri")
                    }
                }.addOnFailureListener { exception ->
                    Log.e("Upload", "Failed to upload image: ${exception.message}")
                }
            } ?: run {
                Log.e("Upload", "Failed to compress image.")
            }
        }
    }

    private fun fetchOfferStatus() {
        offerStatusRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentStatus = snapshot.getValue(String::class.java)
                val offerStatusSpinner: Spinner = findViewById(R.id.spinnerOfferSelect)
                val offerStatusArray = resources.getStringArray(R.array.offer_status_array)

                if (currentStatus != null) {
                    val index = offerStatusArray.indexOf(currentStatus)
                    if (index != -1) {
                        offerStatusSpinner.setSelection(index)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminPage, "Failed to fetch offer status", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupOfferStatusSpinner() {
        val offerStatusSpinner: Spinner = findViewById(R.id.spinnerOfferSelect)
        val offerStatusArray = resources.getStringArray(R.array.offer_status_array)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, offerStatusArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        offerStatusSpinner.adapter = adapter

        // Store the selected offer status locally
        offerStatusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedOfferStatus = offerStatusArray[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle case when no selection is made (optional)
            }
        }
    }

    private fun uploadOfferStatusToServer() {
        selectedOfferStatus?.let { status ->
            offerStatusRef.setValue(status)
                .addOnSuccessListener {
                    // Display a Toast message on success
                    Toast.makeText(this, "Offer status updated successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    // Display a Toast message on failure
                    Toast.makeText(this, "Failed to update offer status. Please try again.", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            // Handle case where no status is selected
            Toast.makeText(this, "Please select an offer status before saving.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupStatusSpinner() {
        val spinnerStatus: Spinner = findViewById(R.id.spinnerStatus)

        // Fetch the restaurant status from Firebase
        database.child("restaurantStatus").get().addOnSuccessListener { snapshot ->
            val status = snapshot.getValue(String::class.java)

            if (status != null) {
                val spinnerPosition = when (status) {
                    "Open" -> 0 // Assuming "Open" is the first item in the array
                    "Close" -> 1 // Assuming "Close" is the second item in the array
                    else -> -1 // Invalid status
                }
                if (spinnerPosition != -1) {
                    spinnerStatus.setSelection(spinnerPosition)
                    updateSpinnerColor(status) // Update color based on fetched status
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch status", Toast.LENGTH_SHORT).show()
        }

        // Set the spinner's item selected listener
        spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedStatus = parent.getItemAtPosition(position).toString()
                updateSpinnerColor(selectedStatus) // Update color based on selected item
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun updateSpinnerColor(status: String) {
        val spinnerStatus = findViewById<Spinner>(R.id.spinnerStatus)
        if (status == "Open") {
            spinnerStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        } else {
            spinnerStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        }
    }

    private fun updateRestaurantStatus() {
        val spinnerStatus = findViewById<Spinner>(R.id.spinnerStatus)
        val selectedStatus = spinnerStatus.selectedItem.toString()

        // Save the selected status to Firebase
        database.child("restaurantStatus").setValue(selectedStatus).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Status updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.profile_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                // Handle profile icon click (e.g., show dropdown or go to profile screen)
                showProfileDropdown(findViewById(R.id.action_profile))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showProfileDropdown(view: View) {
        // Create a PopupMenu for the dropdown
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.logout_menu, popupMenu.menu)

        // Handle menu item clicks
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_logout -> {
                    // Handle logout logic here
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun showLocationPopup() {
        // Inflate the popup view
        val popupView = layoutInflater.inflate(R.layout.popup_add_location, null)

        // Create the AlertDialog
        val alertDialog = AlertDialog.Builder(this)
            .setView(popupView)
            .create()

        // Get references to views inside the popup
        val editTextCoordinates: EditText = popupView.findViewById(R.id.editTextCoordinates)
        val buttonDone: Button = popupView.findViewById(R.id.buttonDoneLocation)
        val textViewPreviousCoordinates: TextView = popupView.findViewById(R.id.textViewPreviousCoordinates)
        val buttonViewOnMaps: Button = popupView.findViewById(R.id.buttonViewOnMaps)

        // Fetch and display the previous coordinates from Firebase
        database.child("coordinates").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val previousCoordinates = snapshot.getValue(String::class.java)
                textViewPreviousCoordinates.text = previousCoordinates ?: "No coordinates set"
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminPage, "Failed to fetch coordinates", Toast.LENGTH_SHORT).show()
            }
        })

        // Set click listener for the Done button
        buttonDone.setOnClickListener {
            val coordinates = editTextCoordinates.text.toString()

            if (coordinates.isNotEmpty()) {
                // Update the coordinates to Firebase
                database.child("coordinates").setValue(coordinates).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        textViewPreviousCoordinates.text = coordinates // Update TextView with new coordinates
                        Toast.makeText(this, "Coordinates updated successfully", Toast.LENGTH_SHORT).show()
                        alertDialog.dismiss() // Close the dialog
                    } else {
                        Toast.makeText(this, "Failed to update coordinates", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Please enter coordinates", Toast.LENGTH_SHORT).show()
            }
        }

        // Set click listener for the View on Maps button
        buttonViewOnMaps.setOnClickListener {
            val coordinates = textViewPreviousCoordinates.text.toString()
            if (coordinates != "No coordinates set") {
                val uri = Uri.parse("geo:0,0?q=$coordinates")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                startActivity(intent)
            } else {
                Toast.makeText(this, "No coordinates available to view on maps", Toast.LENGTH_SHORT).show()
            }
        }

        // Show the dialog
        alertDialog.show()
    }

    private fun showPhoneNumberPopup() {
        // Inflate the popup view
        val popupView = layoutInflater.inflate(R.layout.popup_phone_number, null)

        // Create the AlertDialog
        val alertDialog = AlertDialog.Builder(this)
            .setView(popupView)
            .create()

        // Get references to views inside the popup
        val editTextPhoneNumber: EditText = popupView.findViewById(R.id.editTextPhoneNumber)
        val buttonDone: Button = popupView.findViewById(R.id.buttonDonePhone)
        val textViewPreviousPhoneNumber: TextView = popupView.findViewById(R.id.textViewPreviousPhoneNumber)
        val buttonCallPhoneNumber: Button = popupView.findViewById(R.id.buttonCallPhoneNumber)

        // Fetch and display the previous phone number from Firebase
        database.child("phoneNumber").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val previousPhoneNumber = snapshot.getValue(String::class.java)
                textViewPreviousPhoneNumber.text = previousPhoneNumber ?: "No phone number set"
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminPage, "Failed to fetch phone number", Toast.LENGTH_SHORT).show()
            }
        })

        // Set click listener for the Done button
        buttonDone.setOnClickListener {
            val phoneNumber = editTextPhoneNumber.text.toString()

            if (phoneNumber.isEmpty()) {
                Toast.makeText(this@AdminPage, "Please enter a phone number", Toast.LENGTH_SHORT).show()
            } else if (phoneNumber.length != 10 || !phoneNumber.all { it.isDigit() }) {
                Toast.makeText(this@AdminPage, "Please enter a valid 10-digit phone number", Toast.LENGTH_SHORT).show()
            } else {
                // Update the phone number to Firebase
                database.child("phoneNumber").setValue(phoneNumber).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        textViewPreviousPhoneNumber.text = phoneNumber // Update the TextView with the new phone number
                        Toast.makeText(this@AdminPage, "Phone number updated successfully", Toast.LENGTH_SHORT).show()
                        alertDialog.dismiss() // Close the dialog
                    } else {
                        Toast.makeText(this@AdminPage, "Failed to update phone number", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Set click listener for the Call button
        buttonCallPhoneNumber.setOnClickListener {
            val phoneNumber = textViewPreviousPhoneNumber.text.toString()
            if (phoneNumber != "No phone number set") {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                startActivity(intent)
            } else {
                Toast.makeText(this@AdminPage, "No phone number available to call", Toast.LENGTH_SHORT).show()
            }
        }

        // Show the dialog
        alertDialog.show()
    }


    //Menu related code below

    private fun showSplashScreen() {
        Toast.makeText(this, "Loading, please wait...", Toast.LENGTH_SHORT).show()
    }

    private fun showAddItemPopup() {
        val dialog = Dialog(this)
        val view: View = LayoutInflater.from(this).inflate(R.layout.popup_add_item, null)
        dialog.setContentView(view)

        imagePreview = view.findViewById(R.id.imagePreview)
        val editTextItemName: EditText = view.findViewById(R.id.editTextItemName)
        val editTextPrice: EditText = view.findViewById(R.id.editTextPrice)
        val buttonDoneItemAdd: Button = view.findViewById(R.id.buttonDoneItemAdd)

        imagePreview.setOnClickListener {
            openGalleryForImage()
        }

        buttonDoneItemAdd.setOnClickListener {
            val itemName = editTextItemName.text.toString()
            val itemPrice = editTextPrice.text.toString()

            if (itemName.isEmpty() || itemPrice.isEmpty() || selectedImageUri == null) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newItem = MenuItem(
                imageUri = selectedImageUri.toString(),
                name = itemName,
                price = itemPrice
            )
            menuItems.add(newItem)
            adapter.notifyItemInserted(menuItems.size - 1)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openGalleryForImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            compressAndSetImage(selectedImageUri)
        }
    }

    private fun compressAndSetImage(imageUri: Uri?) {
        imageUri?.let {
            Glide.with(this)
                .asBitmap()
                .load(it)
                .apply(RequestOptions().format(DecodeFormat.PREFER_ARGB_8888).encodeQuality(80))
                .into(imagePreview)
        }
    }

    private fun fetchMenuItems() {
        firestore.collection("menuItems")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { querySnapshot ->
                menuItems.clear()
                originalMenuItems.clear()

                val documents = querySnapshot.documents

                for (document in documents) {
                    val name = document.getString("name") ?: ""
                    val price = document.getString("price") ?: ""
                    val imageUri = document.getString("imageUri") ?: ""
                    val documentId = document.id
                    val timestamp = document.getLong("timestamp") ?: System.currentTimeMillis()

                    val menuItem = MenuItem(imageUri, name, price, documentId, timestamp)
                    menuItems.add(menuItem)
                    originalMenuItems.add(menuItem.copy())
                }
                adapter.notifyDataSetChanged()
                saveMenuItemMetadata(documents)
                Toast.makeText(this, "Data loaded successfully.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching menu items: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveMenuItemMetadata(documents: List<DocumentSnapshot>) {
        val editor = sharedPreferences.edit()
        editor.putInt("itemCount", documents.size)
        documents.forEachIndexed { index, document ->
            editor.putString("item_$index", document.id)
            editor.putString("name_$index", document.getString("name"))
            editor.putString("price_$index", document.getString("price"))
            editor.putString("imageUri_$index", document.getString("imageUri"))
        }
        editor.apply()
    }

    private fun handleMenuChanges(buttonDoneHome: Button) {
        val newItems = menuItems.filter { it !in originalMenuItems }
        val removedItems = originalMenuItems.filter { it !in menuItems }

        if (newItems.isEmpty() && removedItems.isEmpty()) {
            Toast.makeText(this, "No changes detected.", Toast.LENGTH_SHORT).show()
            return
        }

        buttonDoneHome.isClickable = false
        buttonDoneHome.setBackgroundColor(ContextCompat.getColor(this, R.color.darker_gray))
        buttonDoneHome.text = "Updating..."
        val animation = AnimationUtils.loadAnimation(this, R.anim.ripple_animation)
        buttonDoneHome.startAnimation(animation)

        val totalOperations = newItems.size + removedItems.size
        var completedOperations = 0

        for (menuItem in newItems) {
            uploadImageToStorage(menuItem) { imageUri ->
                if (imageUri != null) {
                    saveMenuItemToFirestore(menuItem, imageUri)
                }
                completedOperations++
                checkIfComplete(completedOperations, totalOperations, buttonDoneHome)
            }
        }

        for (removedItem in removedItems) {
            removeItemFromFirestore(removedItem) {
                completedOperations++
                checkIfComplete(completedOperations, totalOperations, buttonDoneHome)
            }
        }

        if (totalOperations == 0) {
            fetchMenuItems()
        }
    }

    private fun checkIfComplete(completedOperations: Int, totalOperations: Int, buttonDoneHome: Button) {
        if (completedOperations == totalOperations) {
            fetchMenuItems()
            buttonDoneHome.clearAnimation()
            buttonDoneHome.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
            buttonDoneHome.isClickable = true
            buttonDoneHome.text = "Done"
        }
    }

    private fun removeItemFromFirestore(menuItem: MenuItem, onComplete: () -> Unit) {
        val storageRef = storage.getReferenceFromUrl(menuItem.imageUri)
        storageRef.delete().addOnSuccessListener {
            firestore.collection("menuItems").document(menuItem.documentId!!)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Item removed successfully", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error removing item: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to delete image: ${it.message}", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    }

    private fun saveMenuItemToFirestore(menuItem: MenuItem, imageUri: String) {
        val itemData = hashMapOf(
            "name" to menuItem.name,
            "price" to menuItem.price,
            "imageUri" to imageUri,
            "timestamp" to menuItem.timestamp
        )

        firestore.collection("menuItems")
            .add(itemData)
            .addOnSuccessListener {
                Toast.makeText(this, "Menu item uploaded successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error uploading menu item: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImageToStorage(menuItem: MenuItem, onComplete: (String?) -> Unit) {
        val storageRef = storage.reference.child("menu_images/${UUID.randomUUID()}.jpg")
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val data = baos.toByteArray()

        val uploadTask = storageRef.putBytes(data)
        uploadTask.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                onComplete(uri.toString())
            }.addOnFailureListener {
                onComplete(null)
            }
        }.addOnFailureListener {
            onComplete(null)
        }
    }

    //Menu related code above

}

