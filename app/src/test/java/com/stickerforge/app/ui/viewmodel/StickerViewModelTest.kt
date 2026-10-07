package com.stickerforge.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StickerViewModelTest {

    private lateinit var viewModel: StickerViewModel

    @Before
    fun setUp() {
        viewModel = StickerViewModel()
    }

    @Test
    fun testInitialState() {
        val state = viewModel.uiState.value
        assertEquals(CurrentScreen.PACKS_LIST, state.currentScreen)
        assertEquals(1, state.packs.size)
        assertFalse("El pack inicial debe ser estatico por defecto", state.packs.first().isAnimated)
    }

    @Test
    fun testCreateNewPackDebounce() {
        val createdFirst = viewModel.createNewPack("Pack Alpha", isAnimated = false)
        assertTrue("La primera creacion debe ser exitosa", createdFirst)

        // Intento inmediato de creacion (simulando spam de clics)
        val createdSpam = viewModel.createNewPack("Pack Spam", isAnimated = false)
        assertFalse("El spam inmediato debe ser bloqueado por debounce", createdSpam)
    }

    @Test
    fun testPackTypeDistinction() {
        viewModel.createNewPack("Pack Estatico", isAnimated = false)
        Thread.sleep(450)
        viewModel.createNewPack("Pack Animado", isAnimated = true)

        val state = viewModel.uiState.value
        val animatedPacks = state.packs.filter { it.isAnimated }
        val staticPacks = state.packs.filter { !it.isAnimated }

        assertEquals(1, animatedPacks.size)
        assertEquals("Pack Animado", animatedPacks.first().name)
        assertTrue(animatedPacks.first().isAnimated)

        assertTrue(staticPacks.any { it.name == "Pack Estatico" && !it.isAnimated })
    }

    @Test
    fun testPackFilterAndSearch() {
        viewModel.createNewPack("Gatitos Tiernos", isAnimated = false)
        Thread.sleep(450)
        viewModel.createNewPack("Perritos Animados", isAnimated = true)

        // Filtro por tipo
        viewModel.setFilter(PackFilter.STATIC)
        var filtered = viewModel.uiState.value.filteredPacks
        assertTrue(filtered.all { !it.isAnimated })

        viewModel.setFilter(PackFilter.ANIMATED)
        filtered = viewModel.uiState.value.filteredPacks
        assertTrue(filtered.all { it.isAnimated })

        // Filtro con busqueda
        viewModel.setFilter(PackFilter.ALL)
        viewModel.setSearchQuery("Gatitos")
        filtered = viewModel.uiState.value.filteredPacks
        assertEquals(1, filtered.size)
        assertEquals("Gatitos Tiernos", filtered.first().name)
    }

    @Test
    fun testDeletePack() {
        viewModel.createNewPack("Pack Para Borrar", isAnimated = false)
        val packId = viewModel.uiState.value.activePack.id

        viewModel.deletePack(packId)
        val exists = viewModel.uiState.value.packs.any { it.id == packId }
        assertFalse("El pack debe haberse eliminado", exists)
    }

    @Test
    fun testPackPublisherCustomization() {
        viewModel.createNewPack("Pack Personalizado", publisher = "Artista Zinko", isAnimated = false)
        val pack = viewModel.uiState.value.activePack
        assertEquals("Pack Personalizado", pack.name)
        assertEquals("Artista Zinko", pack.publisher)

        viewModel.updatePackDetails(pack.id, "Pack Renombrado", "Nuevo Artista")
        val updatedPack = viewModel.uiState.value.activePack
        assertEquals("Pack Renombrado", updatedPack.name)
        assertEquals("Nuevo Artista", updatedPack.publisher)
    }
}
