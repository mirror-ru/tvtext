package com.tvtext.mobile.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials never leave the app sandbox; the encryption key is non-exportable. */
class ProfileStore(context: Context) {
    data class State(val profiles: LinkedHashMap<String, Tv>, val names: LinkedHashMap<String, String>)
    private val file = AtomicFile(File(context.noBackupFilesDir, "televisions.bin"))
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("tvtext-mobile-profiles-v1", null) as? SecretKey)?.let { return it }
        check(create) { "Profile key unavailable" }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("tvtext-mobile-profiles-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized fun load(): LinkedHashMap<String, Tv> = loadState().let { state ->
        LinkedHashMap(state.profiles.mapValues { (id,tv) -> tv.copy(alias=state.names[id].orEmpty()) })
    }
    @Synchronized fun loadState(): State {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return State(linkedMapOf(),linkedMapOf())
        val encoded = file.openRead().use { input ->
            val buffer = ByteArray(262145); var size = 0
            while (size < buffer.size) { val count = input.read(buffer, size, buffer.size - size); if (count < 0) break; size += count }
            check(size <= 262144); buffer.copyOf(size)
        }
        val sealed = DataInputStream(ByteArrayInputStream(encoded)); val version=sealed.readInt();check(version in 1..2)
        val ivSize = sealed.readUnsignedByte(); check(ivSize == 12)
        val iv = ByteArray(ivSize).also { sealed.readFully(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, iv)) }
        val input = DataInputStream(ByteArrayInputStream(cipher.doFinal(ByteArray(sealed.available()).also { sealed.readFully(it) })))
        val count = input.readInt(); check(count in 0..100)
        val result = linkedMapOf<String, Tv>()
        val names = linkedMapOf<String, String>()
        val ids = hashSetOf<String>()
        repeat(count) {
            val tv = Tv(input.readUTF(), input.readUTF(), Endpoint(input.readUTF(), input.readInt()), input.readUTF(), input.readUTF(), input.readUTF())
            check(Wire.validId(tv.id) && tv.name.length in 1..160 && (tv.alias.isEmpty() || Wire.validName(tv.alias)))
            check(tv.token.isEmpty() && tv.fingerprint.isEmpty() || tv.paired)
            check(ids.add(tv.id))
            if(tv.alias.isNotEmpty())names[tv.id]=tv.alias
            // Older builds used an unpaired profile solely to keep a local name.
            // Migrate that name without keeping an offline device in the TV list.
            if(tv.paired)result[tv.id]=tv.copy(alias="")
        }
        if(version==2) {
            val nameCount=input.readInt();check(nameCount in 0..256)
            repeat(nameCount) {
                val id=input.readUTF();val name=input.readUTF()
                check(Wire.validId(id)&&Wire.validName(name)&&names.put(id,name)==null)
            }
        }
        check(input.available() == 0)
        return State(result,names)
    }
    @Synchronized fun save(profiles: Map<String, Tv>) {
        saveState(profiles.filterValues { it.paired },profiles.values.filter { it.alias.isNotEmpty() }.associate { it.id to it.alias })
    }
    @Synchronized fun saveState(profiles: Map<String, Tv>, names: Map<String, String>) {
        check(profiles.size <= 100 && names.size <= 256)
        check(profiles.all { (id,tv) -> id==tv.id&&Wire.validId(id)&&tv.paired&&tv.name.length in 1..160 })
        check(names.all { (id,name) -> Wire.validId(id)&&Wire.validName(name) })
        val clear = ByteArrayOutputStream()
        DataOutputStream(clear).apply {
            writeInt(profiles.size)
            profiles.values.forEach { writeUTF(it.id); writeUTF(it.name); writeUTF(it.endpoint.host); writeInt(it.endpoint.port); writeUTF(""); writeUTF(it.token); writeUTF(it.fingerprint) }
            writeInt(names.size)
            names.forEach { (id,name) -> writeUTF(id);writeUTF(name) }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
        val output = file.startWrite()
        try {
            DataOutputStream(output).apply { writeInt(2); writeByte(cipher.iv.size); write(cipher.iv); write(cipher.doFinal(clear.toByteArray())); flush() }
            file.finishWrite(output)
        } catch (error: Exception) { file.failWrite(output); throw error }
    }
}
