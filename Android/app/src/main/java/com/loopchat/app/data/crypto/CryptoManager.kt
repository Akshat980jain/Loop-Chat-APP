package com.loopchat.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import android.util.Log

/**
 * Handles Hybrid Encryption (RSA for key exchange, AES-GCM for payload).
 * Generates an RSA 2048-bit keypair stored securely in the AndroidKeyStore.
 */
object CryptoManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val RSA_KEY_ALIAS = "LoopChat_RSA_KeyAlias"
    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    init {
        generateRSAKeyPairIfNeeded()
    }

    /**
     * Generates a 2048-bit RSA keypair bounded to the device keystore if it doesn't exist.
     */
    private fun generateRSAKeyPairIfNeeded() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            if (!keyStore.containsAlias(RSA_KEY_ALIAS)) {
                Log.d("CryptoManager", "Generating new RSA KeyPair in AndroidKeyStore")
                val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, 
                    KEYSTORE_PROVIDER
                )
                
                val parameterSpec = KeyGenParameterSpec.Builder(
                    RSA_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                .setKeySize(2048)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .build()
                
                keyPairGenerator.initialize(parameterSpec)
                keyPairGenerator.generateKeyPair()
            } else {
                Log.d("CryptoManager", "RSA KeyPair already exists in AndroidKeyStore")
            }
        } catch (e: Exception) {
            Log.e("CryptoManager", "Error generating RSA KeyPair", e)
        }
    }

    /**
     * Retrieves the public key to upload to the server.
     * Returns Base64 encoded string.
     */
    fun getMyPublicKeyBase64(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val publicKey = keyStore.getCertificate(RSA_KEY_ALIAS)?.publicKey
            if (publicKey != null) {
                Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            Log.e("CryptoManager", "Error retrieving public key", e)
            null
        }
    }

    private fun getMyPrivateKey(): PrivateKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.getKey(RSA_KEY_ALIAS, null) as? PrivateKey
        } catch (e: Exception) {
            Log.e("CryptoManager", "Error retrieving private key", e)
            null
        }
    }

    /**
     * Parses a server-provided Base64 encoded public key string into a PublicKey object.
     */
    fun parsePublicKey(base64Key: String): PublicKey? {
        return try {
            val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Error parsing public key", e)
            null
        }
    }

    /**
     * Encrypts a message payload.
     * 1. Generates a random AES-256 key and IV.
     * 2. Encrypts the plaintext with AES-GCM.
     * 3. Encrypts the AES key with the recipient's RSA public key.
     * 4. Returns a concatenated Base64 string: [encryptedAESKey]:[iv]:[ciphertext]
     * 
     * @param plainText The message to encrypt
     * @param recipientPublicKey The recipient's parsed RSA PublicKey
     */
    fun encryptMessage(plainText: String, recipientPublicKey: PublicKey): String? {
        return try {
            // 1. Generate AES Key
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val aesKey = keyGen.generateKey()
            
            // 2. Encrypt payload with AES-GCM
            val cipherAes = Cipher.getInstance(AES_TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipherAes.init(Cipher.ENCRYPT_MODE, aesKey, spec)
            
            val cipherText = cipherAes.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // 3. Encrypt AES Key with RSA Public Key
            val cipherRsa = Cipher.getInstance(RSA_TRANSFORMATION)
            cipherRsa.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
            val encryptedAesKey = cipherRsa.doFinal(aesKey.encoded)
            
            // 4. Format Output
            val encodedEncryptedAesKey = Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP)
            val encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encodedCipherText = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            
            "$encodedEncryptedAesKey:$encodedIv:$encodedCipherText"
            
        } catch (e: Exception) {
            Log.e("CryptoManager", "Error encrypting message", e)
            null
        }
    }

    /**
     * Decrypts an incoming cipher payload.
     * Expects format: [encryptedAESKey]:[iv]:[ciphertext]
     * 
     * 1. Decrypts the AES Key using the local RSA Private Key.
     * 2. Decrypts the ciphertext using the decrypted AES-GCM Key and IV.
     */
    fun decryptMessage(compositeCipher: String): String? {
        return try {
            val parts = compositeCipher.split(":")
            if (parts.size != 3) {
                Log.e("CryptoManager", "Invalid cipher format. Expected 3 parts, got \${parts.size}")
                return compositeCipher // Return raw if it's not encrypted (e.g. system messages)
            }
            
            val privateKey = getMyPrivateKey() ?: throw Exception("Private key not found")
            
            val encryptedAesKey = Base64.decode(parts[0], Base64.NO_WRAP)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
            
            // 1. Decrypt AES Key
            val cipherRsa = Cipher.getInstance(RSA_TRANSFORMATION)
            cipherRsa.init(Cipher.DECRYPT_MODE, privateKey)
            val aesKeyBytes = cipherRsa.doFinal(encryptedAesKey)
            val aesKey = SecretKeySpec(aesKeyBytes, "AES")
            
            // 2. Decrypt Payload
            val cipherAes = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipherAes.init(Cipher.DECRYPT_MODE, aesKey, spec)
            
            val plainTextBytes = cipherAes.doFinal(cipherText)
            String(plainTextBytes, Charsets.UTF_8)
            
        } catch (e: Exception) {
            Log.e("CryptoManager", "Error decrypting message", e)
            // If decryption fails, it might be a plaintext message from before E2EE was enabled.
            // In a strict environment we'd return an error string, but for migration fallback we return original.
            if(!compositeCipher.contains(":")) compositeCipher else "[Decryption Failed]"
        }
    }
}
