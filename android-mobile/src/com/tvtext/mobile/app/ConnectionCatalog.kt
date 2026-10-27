package com.tvtext.mobile.app

/** Device state and persistence rules, independent of Android views and focus. */
class ConnectionCatalog(private val save: (Map<String, Tv>, Map<String, String>) -> Unit) {
    private val profiles = linkedMapOf<String, Tv>()
    private val localNames = linkedMapOf<String, String>()
    private val discovered = linkedMapOf<String, Tv>()
    private val invalidKeys = hashSetOf<String>()

    fun restore(connections: Map<String, Tv>, names: Map<String, String>) {
        profiles.clear(); profiles.putAll(connections.mapValues { it.value.copy(alias = "") })
        localNames.clear(); localNames.putAll(names)
        discovered.clear(); invalidKeys.clear()
    }
    fun hasPairings() = profiles.values.any { it.paired }
    fun profile(id: String): Tv? = profiles[id]
    fun advertisement(id: String): Tv? = discovered[id]
    fun advertisements(): List<Tv> = discovered.values.toList()
    fun invalidIds(): List<String> = invalidKeys.toList()
    fun restoreInvalidIds(ids: Collection<String>) {
        invalidKeys.clear()
        invalidKeys.addAll(ids.filter { it in profiles })
    }
    fun beginDiscovery() { discovered.clear() }
    fun discover(tv: Tv) { discovered[tv.id] = tv.copy(token = "", fingerprint = "", alias = "") }
    fun invalidate(id: String) { invalidKeys.add(id) }
    fun validate(id: String) { invalidKeys.remove(id) }

    fun pair(tv: Tv) {
        require(tv.paired)
        replaceProfiles(LinkedHashMap(profiles).apply { put(tv.id, tv) })
        invalidKeys.remove(tv.id)
    }
    fun forget(id: String) {
        replaceProfiles(LinkedHashMap(profiles).apply { remove(id) })
        invalidKeys.remove(id)
        discovered.remove(id)
    }
    fun rememberEndpoint(tv: Tv) {
        val previous = profiles[tv.id] ?: return
        if (previous.endpoint != tv.endpoint)
            replaceProfiles(LinkedHashMap(profiles).apply { put(tv.id, previous.copy(endpoint = tv.endpoint)) })
    }

    fun choices(): List<Tv> {
        val devices = LinkedHashMap(profiles)
        discovered.forEach { (id, advertisement) ->
            devices[id] = profiles[id]?.copy(name = advertisement.name, endpoint = advertisement.endpoint)
                ?: advertisement
        }
        return devices.values.map { it.copy(alias = localNames[it.id].orEmpty()) }
    }

    fun isPaired(tv: Tv) = tv.paired && tv.id !in invalidKeys

    private fun replaceProfiles(next: Map<String, Tv>) {
        val connections = next.mapValues { it.value.copy(alias = "") }
        // A failed write leaves the visible device state intact.
        save(connections.toMap(), localNames.toMap())
        profiles.clear()
        profiles.putAll(connections)
    }

    fun rename(id: String, name: String) {
        require(Wire.validId(id) && Wire.validName(name))
        val names = LinkedHashMap(localNames).apply { put(id, name.trim()) }
        save(profiles.toMap(), names.toMap())
        localNames.clear()
        localNames.putAll(names)
    }
}
