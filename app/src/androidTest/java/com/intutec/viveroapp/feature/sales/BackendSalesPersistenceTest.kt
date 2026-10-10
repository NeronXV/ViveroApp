package com.intutec.viveroapp.feature.sales

import android.os.Build
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.intutec.viveroapp.core.database.ViveroDatabase
import com.intutec.viveroapp.feature.cart.data.local.BackendSaleAttemptEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BackendSalesPersistenceTest {
    @Test fun reopeningRoomPreservesDraftAndOriginalUncertainKeyAndIdentity() = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk") || Build.MODEL.contains("Emulator"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        // Unique synthetic database. Never open or clear the operational database.
        val name="stage6-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,ViveroDatabase::class.java,name).build()
        val first=open()
        val id:Long
        try {
            first.backendCartDao().add(2,3,1,"Monstera","maceta",25000)
            first.backendCartDao().add(2,3,1,"Monstera","maceta",25000)
            id=first.backendSaleAttemptDao().insert(BackendSaleAttemptEntity(key="a".repeat(64),actorId=2,branchId=3,
                expectedTotalCents=50000,state="UNCERTAIN"),listOf(1L to 2))
        } finally { first.close() }
        val reopened=open()
        try {
            val row=reopened.backendSaleAttemptDao().load(id)!!
            assertEquals("a".repeat(64),row.attempt.key);assertEquals("UNCERTAIN",row.attempt.state)
            assertEquals(50000L,row.attempt.expectedTotalCents);assertEquals(2,row.items.single().quantity)
            assertEquals(listOf(id),reopened.backendSaleAttemptDao().pending(2,3))
            assertTrue(reopened.backendSaleAttemptDao().pending(2,4).isEmpty())
            assertEquals(2,reopened.backendCartDao().load(2,3)!!.items.single().quantity)
            assertNull(reopened.backendCartDao().load(2,4))
        } finally { reopened.close() }
        // Retain the synthetic file for inspection; the read-only AVD discards its temporary copy on exit.
    }
}
