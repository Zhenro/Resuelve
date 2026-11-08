package com.resuelve.preciosve.data.bcv

import com.resuelve.preciosve.domain.bcv.BcvRateSource
import com.resuelve.preciosve.domain.bcv.RateStore
import com.resuelve.preciosve.domain.model.BcvRate
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BcvRateManagerTest {

    private class FakeSource(
        override val name: String,
        private val behavior: suspend () -> Double
    ) : BcvRateSource {
        var calls = 0

        override suspend fun fetchVesPerRef(): Double {
            calls++
            return behavior()
        }
    }

    private class InMemoryRateStore(var stored: BcvRate? = null) : RateStore {
        var saves = 0

        override fun load(): BcvRate? = stored

        override fun save(rate: BcvRate) {
            stored = rate
            saves++
        }
    }

    private var now = 1_000_000L

    private fun TestScope.manager(
        sources: List<BcvRateSource>,
        store: RateStore,
        ttl: Long = 3_600_000L,
        timeout: Long = 20_000L
    ) = BcvRateManager(
        sources = sources,
        store = store,
        clock = { now },
        freshTtlMillis = ttl,
        sourceTimeoutMillis = timeout,
        ioDispatcher = StandardTestDispatcher(testScheduler)
    )

    @Test
    fun `usa la primera fuente que responde y persiste la tasa`() = runTest {
        val store = InMemoryRateStore()
        val first = FakeSource("a") { 880.5 }
        val second = FakeSource("b") { 900.0 }

        val result = manager(listOf(first, second), store).refresh()

        assertEquals(880.5, result!!.vesPerRef, 0.0)
        assertEquals("a", result.source)
        assertEquals(0, second.calls)
        assertEquals(1, store.saves)
        assertEquals(result, store.stored)
    }

    @Test
    fun `si la primera falla usa la segunda`() = runTest {
        val store = InMemoryRateStore()
        val failing = FakeSource("a") { throw IOException("caída") }
        val ok = FakeSource("b") { 77.0 }

        val manager = manager(listOf(failing, ok), store)
        val result = manager.refresh()

        assertEquals("b", result!!.source)
        assertEquals(result, manager.rate.value)
    }

    @Test
    fun `ignora valores invalidos de una fuente`() = runTest {
        val store = InMemoryRateStore()
        val zero = FakeSource("zero") { 0.0 }
        val nan = FakeSource("nan") { Double.NaN }
        val ok = FakeSource("ok") { 12.0 }

        val result = manager(listOf(zero, nan, ok), store).refresh()

        assertEquals("ok", result!!.source)
    }

    @Test
    fun `si todas fallan conserva la ultima tasa guardada`() = runTest {
        val stale = BcvRate(850.0, fetchedAtMillis = now - 7_200_000L, source = "vieja")
        val store = InMemoryRateStore(stale)
        val failing = FakeSource("a") { throw IOException("sin red") }

        val manager = manager(listOf(failing), store)
        val result = manager.refresh()

        assertEquals(stale, result)
        assertEquals(stale, manager.rate.value)
        assertEquals(1, failing.calls)
    }

    @Test
    fun `si todas fallan y no hay tasa guardada devuelve null`() = runTest {
        val manager = manager(listOf(FakeSource("a") { throw IOException("sin red") }), InMemoryRateStore())

        assertNull(manager.refresh())
        assertNull(manager.rate.value)
    }

    @Test
    fun `una tasa vigente no consulta la red salvo que se fuerce`() = runTest {
        val fresh = BcvRate(850.0, fetchedAtMillis = now - 1_000L, source = "guardada")
        val source = FakeSource("a") { 999.0 }
        val manager = manager(listOf(source), InMemoryRateStore(fresh))

        assertEquals(fresh, manager.refresh())
        assertEquals(0, source.calls)

        assertEquals(999.0, manager.refresh(force = true)!!.vesPerRef, 0.0)
        assertEquals(1, source.calls)
    }

    @Test
    fun `una tasa vencida se actualiza`() = runTest {
        val stale = BcvRate(850.0, fetchedAtMillis = now - 7_200_000L, source = "vieja")
        val source = FakeSource("a") { 910.0 }

        val result = manager(listOf(source), InMemoryRateStore(stale)).refresh()

        assertEquals(910.0, result!!.vesPerRef, 0.0)
        assertEquals(1, source.calls)
    }

    @Test
    fun `una fuente lenta se descarta por timeout y se prueba la siguiente`() = runTest {
        val slow = FakeSource("lenta") {
            delay(60_000L)
            1.0
        }
        val ok = FakeSource("ok") { 5.0 }

        val result = manager(listOf(slow, ok), InMemoryRateStore(), timeout = 1_000L).refresh()

        assertEquals("ok", result!!.source)
        assertEquals(5.0, result.vesPerRef, 0.0)
    }

    @Test
    fun `un fallo al persistir no invalida la tasa en memoria`() = runTest {
        val store = object : RateStore {
            override fun load(): BcvRate? = null
            override fun save(rate: BcvRate) {
                throw IllegalStateException("disco lleno")
            }
        }
        val manager = manager(listOf(FakeSource("a") { 33.0 }), store)

        assertEquals(33.0, manager.refresh()!!.vesPerRef, 0.0)
        assertEquals(33.0, manager.rate.value!!.vesPerRef, 0.0)
    }

    @Test
    fun `un fallo al leer el almacen se trata como sin tasa guardada`() = runTest {
        val store = object : RateStore {
            override fun load(): BcvRate? {
                throw IllegalStateException("corrupto")
            }

            override fun save(rate: BcvRate) = Unit
        }
        val manager = manager(listOf(FakeSource("a") { 44.0 }), store)

        assertEquals(44.0, manager.refresh()!!.vesPerRef, 0.0)
    }
}
