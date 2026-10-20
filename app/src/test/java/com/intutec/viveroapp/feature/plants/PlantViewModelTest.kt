package com.intutec.viveroapp.feature.plants

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.intutec.viveroapp.MainDispatcherRule
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.feature.cart.activeBackend
import com.intutec.viveroapp.feature.plants.domain.*
import com.intutec.viveroapp.feature.plants.presentation.PlantViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class PlantViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private class Gateway : PlantGateway {
        var work: PlantWork? = PlantWork(id=9, product=true, name="Ficus", status="PENDING")
        var published = false
        override suspend fun list() = PlantList(emptyList(), emptyList(), if(published) listOf(PlantWork(id=9,product=true,name="Ficus")) else emptyList())
        override suspend fun open(plant: PlantWork) = plant
        override suspend fun save(plant: PlantWork, publish: Boolean): PlantWork {
            published=publish
            return plant.copy(readyForSale=false,needsInitialCount=true)
        }
        override suspend fun recover(): PlantWork? = null
        override suspend fun hasPending() = false
        override suspend fun remember(plant: PlantWork?) { work=plant }
        override suspend fun remembered() = work
    }

    @Test fun confirmedProductReturnsToListAndExplainsPendingInventory() = runTest {
        val sessions=SessionStore()
        activeBackend(sessions,permissions=setOf("MANAGE_PRODUCTS","MANAGE_PRICES","PUBLISH_CATALOGING"))
        val gateway=Gateway()
        val store=ViewModelStore()
        val vm=PlantViewModel(gateway,sessions,mock(Context::class.java))
        store.put("plants",vm)
        try {
            runCurrent()
            vm.save(publish=true)
            runCurrent()
            assertNull(vm.state.value.selected)
            assertNull(gateway.work)
            assertEquals(1,vm.state.value.catalogProducts.size)
            assertTrue(vm.state.value.message!!.contains("Falta confirmar el conteo inicial"))
            assertFalse(vm.state.value.busy)
            assertNull(vm.state.value.error)
        } finally { store.clear() }
    }
}
