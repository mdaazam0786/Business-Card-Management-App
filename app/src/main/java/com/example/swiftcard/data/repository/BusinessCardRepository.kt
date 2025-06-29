package com.example.swiftcard.data.repository

import com.example.swiftcard.data.model.BusinessCard
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BusinessCardRepository @Inject constructor(

) {
    private val databaseReference : DatabaseReference = FirebaseDatabase.getInstance().getReference("BusinessCards")

    suspend fun saveBusinessCard(businessCard: BusinessCard) {
        if (businessCard.id.isEmpty()) {
            // New card: push to generate a unique key
            val newRef = databaseReference.push()
            businessCard.id = newRef.key ?: "" // Get the generated key
            newRef.setValue(businessCard).await()
        } else {
            // Existing card: update by its ID
            databaseReference.child(businessCard.id).setValue(businessCard).await()
        }
    }

    suspend fun deleteBusinessCard(businessCardId: String) {
        databaseReference.child(businessCardId).removeValue().await()
    }

    suspend fun getBusinessCardById(id: String): BusinessCard? {
        val snapshot = databaseReference.child(id).get().await()
        return snapshot.getValue(BusinessCard::class.java)
    }

    fun getAllBusinessCardsRealtime(): Flow<List<BusinessCard>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cards = snapshot.children.mapNotNull { dataSnapshot ->
                    val card = dataSnapshot.getValue(BusinessCard::class.java)
                    // Ensure the ID is set from the Firebase key
                    card?.apply { id = dataSnapshot.key ?: "" }
                }
                trySend(cards)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        databaseReference.addValueEventListener(listener)

        awaitClose { databaseReference.removeEventListener(listener) }
    }


}