package com.tutpro.baresip.plus

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.createScaledBitmap
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentResolverCompat.query
import androidx.core.content.ContextCompat
import java.io.*
import java.net.InetAddress
import java.security.SecureRandom
import java.util.*
import java.util.zip.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.ArrayList

object Utils {

    fun getNameValue(string: String, name: String): ArrayList<String> {
        val lines = string.split("\n")
        val result = ArrayList<String>()
        for (line in lines) {
            if (line.startsWith(name))
                result.add((line.substring(name.length).trim()).split(" \t")[0])
        }
        return result
    }

    fun removeLinesStartingWithString(lines: String, string: String): String {
        var result = ""
        for (line in lines.split("\n"))
            if (!line.startsWith(string) && (line.length > 0)) result += line + "\n"
        return result
    }

    fun alertView(context: Context, title: String, message: String, action: () -> (Unit) = {}) {
        val titleView = View.inflate(context, R.layout.alert_title, null) as TextView
        titleView.text = title
        with(AlertDialog.Builder(context, R.style.AlertDialog)) {
            setCustomTitle(titleView)
            setMessage(message)
            setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                action()
            }
            val dialog = this.create()
            dialog.show()
            if (darkTheme())
                dialog.window!!.setBackgroundDrawableResource(R.color.colorDark)
        }
    }

    fun uriHostPart(uri: String): String {
        return if (uri.contains("@")) {
            uri.substringAfter("@")
                    .substringBefore(":")
                    .substringBefore(";")
                    .substringBefore(">")
        } else {
            val parts = uri.split(":")
            when (parts.size) {
                2 -> parts[1].substringBefore(";")
                        .substringBefore(">")
                3 -> parts[1]
                else -> ""
            }
        }
    }

    fun uriUserPart(uri: String): String {
        return if (uri.contains("@"))
            uri.substringAfter(":").substringBefore("@")
        else
            ""
    }

    private fun uriParams(uri: String): List<String> {
        val params = uri.split(";")
        return if (params.size == 1) listOf() else params.subList(1, params.size)
    }

    fun friendlyUri(uri: String, domain: String): String {
        var u = uri
        if (uri.startsWith("<") && (uri.endsWith(">")))
            u = uri.substring(1).substringBeforeLast(">")
        if (u.contains("@")) {
            val user = uriUserPart(u)
            val host = uriHostPart(u)
            return if (isE164Number(user) || (host == domain))
                user
            else
                "$user@$host"
        } else {
            return u
        }
    }

    fun uriComplete(uri: String, domain: String): String {
        val res = if (!uri.startsWith("sip:")) "sip:$uri" else uri
        return if (checkUriUser(uri)) "$res@$domain" else res
    }

    fun aorDomain(aor: String): String {
        return uriHostPart(aor)
    }

    fun plainAor(aor: String): String {
        return uriUserPart(aor) + "@" + uriHostPart(aor)
    }

    fun checkAor(aor: String): Boolean {
        if (!checkSipUri(aor)) return false
        val params = uriParams(aor)
        return params.isEmpty() ||
                ((params.size == 1) &&
                        params[0] in arrayOf("transport=udp", "transport=tcp", "transport=tls"))
    }

    fun checkStunUri(uri: String): Boolean {
        if (!uri.startsWith("stun:") && !uri.startsWith("turn:"))
            return false
        return checkHostPort(uri.substringAfter(":"))
    }

    fun isE164Number(no: String): Boolean {
        return Regex("^[+][1-9][0-9]{0,14}\$").matches(no)
    }

    fun checkIpV4(ip: String): Boolean {
        return Regex("^(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))$").matches(ip)
    }

    private fun checkIpV6(ip: String): Boolean {
        return Regex("^(([0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4})$").matches(ip)
    }

    private fun checkIpv6InBrackets(bracketedIp: String): Boolean {
        return bracketedIp.startsWith("[") && bracketedIp.endsWith("]") &&
                checkIpV6(bracketedIp.substring(1, bracketedIp.length - 2))
    }

    private fun checkUriUser(user: String): Boolean {
        user.forEach { if (!(it.isLetterOrDigit() || "-_.!~*\'()&=+\$,;?/".contains(it))) return false }
        return user.isNotEmpty() && !checkIpV4(user) && !checkIpV6(user)
    }

    fun checkDomain(domain: String): Boolean {
        val parts = domain.split(".")
        for (p in parts) {
            if (p.endsWith("-") || p.startsWith("-") ||
                    !Regex("^[-a-zA-Z0-9]+\$").matches(p))
                return false
        }
        return true
    }

    private fun checkPort(port: String): Boolean {
        val number = port.toIntOrNull() ?: return false
        return (number > 0) && (number < 65536)
    }

    fun checkIpPort(ipPort: String): Boolean {
        if (ipPort.startsWith("["))
            return checkIpv6InBrackets(ipPort.substringBeforeLast(":")) &&
                    checkPort(ipPort.substringAfterLast(":"))
        else
            return checkIpV4(ipPort.substringBeforeLast(":")) &&
                    checkPort(ipPort.substringAfterLast(":"))
    }

    private fun checkDomainPort(domainPort: String): Boolean {
        return checkDomain(domainPort.substringBeforeLast(":")) &&
                checkPort(domainPort.substringAfterLast(":"))
    }

    private fun checkHostPort(hostPort: String): Boolean {
        return checkIpV4(hostPort) || checkIpv6InBrackets(hostPort) || checkDomain(hostPort) ||
                checkIpPort(hostPort) || checkDomainPort(hostPort)
    }

    private fun checkParams(params: String): Boolean {
        for (param in params.split(";"))
            if (!checkParam(param)) return false
        return true
    }

    private fun checkParam(param: String): Boolean {
        val nameValue = param.split("=")
        if (nameValue.size == 1)
            /* Todo: do proper check */
            return true
        if (nameValue.size == 2)
            /* Todo: do proper check */
            return true
        return false
    }

    fun paramValue(params: String, name: String): String {
        if (params == "") return ""
        for (param in params.split(";"))
            if (param.substringBefore("=") == name) return param.substringAfter("=")
        return ""
    }

    fun checkHostPortParams(hpp: String) : Boolean {
        val restParams = hpp.split(";", limit = 2)
        if (restParams.size == 1)
            return checkHostPort(restParams[0])
        else
            return checkHostPort(restParams[0]) && checkParams(restParams[1])
    }

    fun checkSipUri(uri: String): Boolean {
        if (!uri.startsWith("sip:")) return false
        val userRest = uri.substring(4).split("@")
        return when (userRest.size) {
            1 ->
                checkHostPortParams(userRest[0])
            2 ->
                checkUriUser(userRest[0]) && checkHostPortParams(userRest[1])
            else -> false
        }
    }

    fun checkName(name: String): Boolean {
        return name.isNotEmpty() && name == String(name.toByteArray(), Charsets.UTF_8) &&
                name.lines().size == 1 && !name.contains('"')
    }

    fun checkIfName(name: String): Boolean {
        if ((name.length < 2) || !name.first().isLetter()) return false
        for (c in name)
            if (!c.isLetterOrDigit()) return false
        return true
    }

    fun findIpV6Address(list: List<LinkAddress>): String {
        for (la in list)
            if (la.scope == android.system.OsConstants.RT_SCOPE_UNIVERSE)
                if (checkIpV6(la.address.hostAddress))
                    return la.address.hostAddress
        return ""
    }

    fun findIpV4Address(list: List<LinkAddress>): String {
        for (la in list)
            if (la.scope == android.system.OsConstants.RT_SCOPE_UNIVERSE)
                if (checkIpV4(la.address.hostAddress))
                    return la.address.hostAddress
        return ""
    }

    fun findDnsServers(list: List<InetAddress>): String {
        var servers = ""
        for (dnsServer in list) {
            var address = dnsServer.hostAddress.removePrefix("/")
            if (checkIpV4(address))
                address = "${address}:53"
            else
                address = "[${address}]:53"
            if (servers == "")
                servers = address
            else
                servers = "${servers},${address}"
        }
        return servers
    }

    private fun updateLinkAddresses(linkAddresses: List<LinkAddress>) {
        var updated = false
        val ipV6Addr = findIpV6Address(linkAddresses)
        if (ipV6Addr != findIpV6Address(BaresipService.linkAddresses)) {
            Log.d("Baresip", "Updating IPv6 address to '$ipV6Addr'")
            if (ipV6Addr != "") {
                if (Api.net_set_address(ipV6Addr) != 0)
                    Log.w("Baresip", "Failed to update net address '$ipV6Addr")
            } else {
                Api.net_unset_address(Api.AF_INET6)
            }
            updated = true
        }
        val ipV4Addr = findIpV4Address(linkAddresses)
        if (ipV4Addr != findIpV4Address(BaresipService.linkAddresses)) {
            Log.d("Baresip", "Updating IPv4 address to '$ipV4Addr'")
            if (ipV4Addr != "") {
                if (Api.net_set_address(ipV4Addr) != 0)
                    Log.w("Baresip", "Failed to update net address '$ipV4Addr'")
            } else {
                Api.net_unset_address(Api.AF_INET)
            }
            updated = true
        }
        if (updated) {
            BaresipService.linkAddresses = linkAddresses
            Api.uag_reset_transp(register = true, reinvite = true)
            Api.net_debug()
        } else {
            UserAgent.register()
        }
    }

    fun updateLinkProperties(props: LinkProperties) {
        if (BaresipService.dynDns && (BaresipService.dnsServers != props.dnsServers)) {
            if (BaresipService.isServiceRunning)
                    if (Config.updateDnsServers(props.dnsServers) != 0)
                        Log.w("Baresip", "Failed to update DNS servers '${props.dnsServers}'")
                    else
                        BaresipService.dnsServers = props.dnsServers
            else
                BaresipService.dnsServers = props.dnsServers
        }
        updateLinkAddresses(props.linkAddresses)
    }

    fun implode(list: List<String>, sep: String): String {
        var res = ""
        for (s in list) {
            if (res == "")
                res = s
            else
                res = res + sep + s
        }
        return res
    }

    fun isVisible(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    fun dtmfWatcher(callp: String): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(sequence: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(sequence: CharSequence, start: Int, before: Int, count: Int) {
                val text = sequence.subSequence(start, start + count).toString()
                if (text.isNotEmpty()) {
                    val digit = text[0]
                    val call = Call.ofCallp(callp)
                    if (call == null) {
                        Log.w("Baresip", "dtmfWatcher did not find call $callp")
                    } else {
                        Log.d("Baresip", "Got DTMF digit '$digit'")
                        if (((digit >= '0') && (digit <= '9')) || (digit == '*') || (digit == '#'))
                            call.sendDigit(digit)
                    }
                }
            }
            override fun afterTextChanged(sequence: Editable) {
                // KEYCODE_REL
                // call_send_digit(callp, 4.toChar())
            }
        }
    }

    fun checkPermission(ctx: Context, permissions: String) : Boolean {
        for (p in permissions.split("|")) {
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }

    fun requestPermission(ctx: Context, permissions: String, requestCode: Int) : Boolean {
        val pArray = permissions.split("|").toTypedArray()
        for (p in pArray)
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ctx as Activity, pArray, requestCode)
                return false
            }
        return true
    }

    fun copyAssetToFile(context: Context, asset: String, path: String) {
        try {
            val `is` = context.assets.open(asset)
            val os = FileOutputStream(path)
            val buffer = ByteArray(512)
            var byteRead: Int = `is`.read(buffer)
            while (byteRead  != -1) {
                os.write(buffer, 0, byteRead)
                byteRead = `is`.read(buffer)
            }
            os.close()
            `is`.close()
        } catch (e: IOException) {
            Log.e("Baresip", "Failed to copy asset '$asset' to file: $e")
        }
    }

    fun deleteFile(file: File) {
        if (file.exists()) {
            try {
                file.delete()
            } catch (e: IOException) {
                Log.e("Baresip", "Could not delete file ${file.absolutePath}")
            }
        }
    }

    fun getFileContents(filePath: String): ByteArray? {
        return try {
            File(filePath).readBytes()
        } catch (e: FileNotFoundException) {
            Log.e("Baresip", "File '$filePath' not found: ${e.printStackTrace()}")
            null
        } catch (e: Exception) {
            Log.e("Baresip", "Failed to read file '$filePath': ${e.printStackTrace()}")
            null
        }
    }

    fun putFileContents(filePath: String, contents: ByteArray): Boolean {
        try {
            File(filePath).writeBytes(contents)
        }
        catch (e: IOException) {
            Log.e("Baresip", "Failed to write file '$filePath': $e")
            return false
        }
        return true
    }

    fun fileNameOfUri(ctx: Context, uri: Uri): String {
        val cursor = ctx.contentResolver.query(uri, null, null, null, null)
        var name = ""
        if (cursor != null) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            name = cursor.getString(index)
            cursor.close()
        }
        return name
    }

    fun saveBitmap(bitmap: Bitmap, file: File): Boolean {
        if (file.exists()) file.delete()
        try {
            val out = FileOutputStream(file)
            val scaledBitmap = createScaledBitmap(bitmap, 96, 96, true)
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Log.e("Baresip", "Saved bitmap to ${file.absolutePath} of length ${file.length()}")
        } catch (e: Exception) {
            Log.e("Baresip", "Failed to save bitmap to ${file.absolutePath}")
            return false
        }
        return true
    }

    class Crypto(val salt: ByteArray, val iter: Int, val iv: ByteArray, val data: ByteArray):
        Serializable {
        val serialVersionUID = -29238082928391L
    }

    private fun encrypt(content: ByteArray, password: CharArray): Crypto? {
        var obj: Crypto? = null
        try {
            val sr = SecureRandom()
            val salt = ByteArray(128)
            sr.nextBytes(salt)
            val iterationCount = Random().nextInt(1024) + 512
            val pbKeySpec = PBEKeySpec(password, salt, iterationCount, 128)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivRandom = SecureRandom()
            val iv = ByteArray(16)
            ivRandom.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val cipherData = cipher.doFinal(content)
            obj = Crypto(salt, iterationCount, iv, cipherData)
        } catch (e: Exception) {
            Log.e("Baresip", "Encrypt failed: ${e.printStackTrace()}")
        }
        return obj

    }

    private fun decrypt(obj: Crypto, password: CharArray): ByteArray? {
        var plainData: ByteArray? = null
        try {
            val pbKeySpec = PBEKeySpec(password, obj.salt, obj.iter, 128)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val ivSpec = IvParameterSpec(obj.iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            plainData = cipher.doFinal(obj.data)
        } catch (e: Exception) {
            Log.e("Baresip", "Decrypt failed: ${e.printStackTrace()}")
        }
        return plainData
    }

    fun encryptToStream(stream: FileOutputStream?, content: ByteArray, password: String): Boolean {
        val obj = encrypt(content, password.toCharArray())
        try {
            ObjectOutputStream(stream).use {
                it.writeObject(obj)
            }
        } catch (e: Exception) {
            Log.w("Baresip", "encryptToStream failed: $e")
            return false
        }
        return true
    }

    fun decryptFromStream(stream: FileInputStream?, password: String): ByteArray? {
        var plainData: ByteArray? = null
        try {
            ObjectInputStream(stream).use {
                val obj = it.readObject() as Crypto
                plainData = decrypt(obj, password.toCharArray())
            }
        } catch (e: Exception) {
            Log.w("Baresip", "decryptFromStream failed: $e")
        }
        return plainData
    }

    fun zip(fileNames: ArrayList<String>, zipFileName: String): Boolean {
        val zipFilePath = BaresipService.filesPath + "/" + zipFileName
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFilePath))).use { out ->
                val data = ByteArray(1024)
                for (file in fileNames) {
                    val filePath = BaresipService.filesPath + "/" + file
                    if (File(filePath).exists()) {
                        FileInputStream(filePath).use { fi ->
                            BufferedInputStream(fi).use { origin ->
                                val entry = ZipEntry(filePath)
                                out.putNextEntry(entry)
                                while (true) {
                                    val readBytes = origin.read(data)
                                    if (readBytes == -1) break
                                    out.write(data, 0, readBytes)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("Baresip", "Failed to zip file '$zipFilePath': $e")
            return false
        }
        return true
    }

    fun unZip(zipFilePath: String): Boolean {
        val allFiles = listOf("accounts", "calls", "config", "contacts", "messages", "uuid",
                "zrtp_cache.dat", "zrtp_zid", "cert.pem", "ca_cert", "ca_certs.crt")
        val zipFiles = mutableListOf<String>()
        try {
            ZipFile(zipFilePath).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    zipFiles.add(entry.name.substringAfterLast("/"))
                    zip.getInputStream(entry).use { input ->
                        File(entry.name).outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("Baresip", "Failed to unzip file '$zipFilePath': $e")
            return false
        }
        (allFiles - zipFiles).iterator().forEach {
            deleteFile(File(BaresipService.filesPath, it))
        }
        return true
    }

    fun dumpIntent(intent: Intent) {
        val bundle: Bundle = intent.extras ?: return
        val keys = bundle.keySet()
        val it = keys.iterator()
        Log.d("Baresip", "Dumping intent start")
        while (it.hasNext()) {
            val key = it.next()
            Log.d("Baresip", "[" + key + "=" + bundle.get(key) + "]")
        }
        Log.d("Baresip", "Dumping intent finish")
    }

    fun randomColor(): Int {
        val rnd = Random()
        return Color.argb(255, rnd.nextInt(256), rnd.nextInt(256),
                rnd.nextInt(256))
    }

    fun addActivity(activity: String) {
        if ((BaresipService.activities.size == 0) || (BaresipService.activities[0] != activity))
            BaresipService.activities.add(0, activity)
    }

    fun supportedCameras(ctx: Context): Map<String, Int> {
        val cameras = mutableMapOf<String, Int>()
        val cm = ctx.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
        try {
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val level = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                if ((level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL) ||
                        (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
                    val dir = chars.get(CameraCharacteristics.LENS_FACING)
                    if (dir != null) cameras.put(id, dir)
                }
            }
        } catch (e: CameraAccessException) {
            Log.e("Baresip", "Could not get supportedCameras: ${e.printStackTrace()}")
        }
        return cameras
    }

    fun darkTheme(): Boolean {
        return AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
    }

    @Suppress("DEPRECATION")
    @Throws(IOException::class)
    fun savePicture(ctx: Context, bitmap: Bitmap, name: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            val resolver: ContentResolver = ctx.contentResolver
            val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(contentUri, contentValues)
                    ?: throw IOException("Failed to create new MediaStore record")
            val stream = resolver.openOutputStream(uri)
                    ?: throw IOException("Failed to open output stream")
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                resolver.delete(uri, null, null)
                stream.close()
                throw IOException("Failed to save bitmap")
            }
            stream.close()
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
            val stream = FileOutputStream(File(imagesDir, name))
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                stream.close()
                throw IOException("Failed to save bitmap")
            }
            stream.close()
        }
    }
}
