package local.tvtext.mobiletests

import com.tvtext.mobile.app.*
import java.io.IOException
import java.util.UUID

object ConnectionCatalogTests {
    fun run() {
        var failWrite = false
        var savedProfiles: Map<String, Tv> = emptyMap()
        var savedNames: Map<String, String> = emptyMap()
        val catalog = ConnectionCatalog { profiles, names ->
            if (failWrite) throw IOException("Fixture write failure")
            savedProfiles = profiles.toMap()
            savedNames = names.toMap()
        }
        val first = Tv(UUID.randomUUID().toString(), "TV", Endpoint("127.0.0.1"), token = "a".repeat(64), fingerprint = "b".repeat(64))
        val other = first.copy(id = UUID.randomUUID().toString(), name = "Other")
        catalog.pair(first); catalog.pair(other)
        catalog.rename(first.id, "Living room")
        catalog.discover(first.copy(endpoint = Endpoint("127.0.0.2"), token = "", fingerprint = ""))
        val relocated = catalog.choices().first()
        check(relocated.endpoint.host == "127.0.0.2" && relocated.token == first.token && relocated.alias == "Living room")
        catalog.invalidate(first.id)
        check(!catalog.isPaired(relocated) && catalog.isPaired(other))
        failWrite = true
        check(runCatching { catalog.forget(first.id) }.isFailure)
        check(catalog.profile(first.id) == first && first.id in savedProfiles)
        check(runCatching { catalog.rename(first.id, "Unsaved") }.isFailure)
        check(catalog.choices().first().alias == "Living room" && savedNames[first.id] == "Living room")
        check(runCatching { catalog.rememberEndpoint(relocated) }.isFailure)
        check(catalog.profile(first.id)?.endpoint == first.endpoint)
        failWrite = false
        catalog.rememberEndpoint(relocated)
        check(catalog.profile(first.id)?.endpoint == relocated.endpoint && catalog.profile(first.id)?.token == first.token)
        catalog.forget(first.id)
        check(catalog.choices().map { it.id } == listOf(other.id))
        check(first.id !in savedProfiles && savedNames[first.id] == "Living room")
        catalog.discover(first.copy(token = "", fingerprint = ""))
        check(catalog.choices().last().alias == "Living room" && !catalog.isPaired(catalog.choices().last()))
        catalog.beginDiscovery()
        check(catalog.choices().map { it.id } == listOf(other.id))
        val restored = mutableMapOf(other.id to other)
        catalog.restore(restored, savedNames); restored.clear()
        check(catalog.hasPairings() && catalog.profile(other.id) == other)
        runCatching { (catalog.choices() as MutableList).clear() }
        check(catalog.choices().size == 1)

        val transient = first.copy(id = UUID.randomUUID().toString(), name = "Found TV")
        catalog.discover(transient) // Never retain advertised credentials in recreation state.
        val snapshot = catalog.advertisements()
        check(snapshot.single().id == transient.id && snapshot.single().token.isEmpty() && snapshot.single().fingerprint.isEmpty())
        catalog.invalidate(other.id)
        val recreated = ConnectionCatalog { _, _ -> }.apply {
            restore(mapOf(other.id to other), emptyMap())
            snapshot.forEach(::discover)
            restoreInvalidIds(catalog.invalidIds() + transient.id)
        }
        check(recreated.choices().map { it.id }.toSet() == setOf(other.id, transient.id))
        check(!recreated.isPaired(recreated.choices().first { it.id == other.id }))
        check(recreated.invalidIds() == listOf(other.id))
        println("PASS: mobile catalog isolates mutable state, credentials and aliases; failed writes preserve pairings/endpoints; unpair removes offline rows")
    }
}
