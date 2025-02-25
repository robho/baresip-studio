package com.tutpro.baresip

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.pm.PackageManager
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.tutpro.baresip.Utils.copyInputStreamToFile
import com.tutpro.baresip.databinding.ActivityConfigBinding
import java.io.File
import java.io.FileInputStream
import java.util.*

class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private lateinit var autoStart: CheckBox
    private lateinit var listenAddr: EditText
    private lateinit var dnsServers: EditText
    private lateinit var certificateFile: CheckBox
    private lateinit var verifyServer: CheckBox
    private lateinit var caFile: CheckBox
    private lateinit var darkTheme: CheckBox
    private lateinit var debug: CheckBox
    private lateinit var sipTrace: CheckBox
    private lateinit var reset: CheckBox

    private var oldAutoStart = ""
    private var oldListenAddr = ""
    private var oldDnsServers = ""
    private var oldCertificateFile = false
    private var oldVerifyServer = ""
    private var oldCAFile = false
    private var oldLogLevel = ""
    private var callVolume = BaresipService.callVolume
    private var oldDisplayTheme = -1
    private var save = false
    private var restart = false
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Utils.addActivity("config")

        autoStart = binding.AutoStart
        val asCv = Config.variable("auto_start")
        oldAutoStart = if (asCv.size == 0) "no" else asCv[0]
        autoStart.isChecked = oldAutoStart == "yes"

        listenAddr = binding.ListenAddress
        val laCv = Config.variable("sip_listen")
        oldListenAddr = if (laCv.size == 0) "" else laCv[0]
        listenAddr.setText(oldListenAddr)

        dnsServers = binding.DnsServers
        val ddCv = Config.variable("dyn_dns")
        if (ddCv[0] == "yes") {
            oldDnsServers = ""
        } else {
            val dsCv = Config.variable("dns_server")
            var dsTv = ""
            for (ds in dsCv) dsTv += ", $ds"
            oldDnsServers = dsTv.trimStart(',').trimStart(' ')
        }
        dnsServers.setText(oldDnsServers)

        certificateFile = binding.CertificateFile
        oldCertificateFile = Config.variable("sip_certificate").isNotEmpty()
        certificateFile.isChecked = oldCertificateFile

        val certificateRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    it.data?.data?.also { uri ->
                        try {
                            val inputStream = applicationContext.contentResolver.openInputStream(uri)
                                    as FileInputStream
                            File(BaresipService.filesPath + "/cert.pem")
                                .copyInputStreamToFile(inputStream)
                            inputStream.close()
                            Config.removeVariable("sip_certificate")
                            Config.addLine("sip_certificate ${BaresipService.filesPath}/cert.pem")
                            save = true
                            restart = true
                        } catch (e: Error) {
                            Utils.alertView(
                                this, getString(R.string.error),
                                getString(R.string.read_cert_error)
                            )
                            certificateFile.isChecked = false
                        }
                    }
                } else {
                    certificateFile.isChecked = false
                }
            }

        certificateFile.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT < 29) {
                    if (!Utils.requestPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            READ_CERT_PERMISSION_CODE)) {
                        certificateFile.isChecked = false
                        return@setOnCheckedChangeListener
                    }
                    val downloadsPath = Utils.downloadsPath("cert.pem")
                    val content = Utils.getFileContents(downloadsPath)
                    if (content == null) {
                        Utils.alertView(this, getString(R.string.error),
                            getString(R.string.read_cert_error))
                        certificateFile.isChecked = false
                        return@setOnCheckedChangeListener
                    }
                    val filesPath = BaresipService.filesPath + "/cert.pem"
                    Utils.putFileContents(filesPath, content)
                    Config.removeVariable("sip_certificate")
                    Config.addLine("sip_certificate $filesPath")
                    save = true
                    restart = true
                } else {
                    Utils.selectInputFile(certificateRequest)
                }
            } else {
                Config.removeVariable("sip_certificate")
                save = true
                restart = true
            }
        }

        verifyServer = binding.VerifyServer
        val vsCv = Config.variable("sip_verify_server")
        oldVerifyServer = if (vsCv.size == 0) "no" else vsCv[0]
        verifyServer.isChecked = oldVerifyServer == "yes"

        caFile = binding.CAFile
        oldCAFile = Config.variable("sip_cafile").isNotEmpty()
        caFile.isChecked = oldCAFile

        val certificatesRequest =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK)
                    it.data?.data?.also { uri ->
                        try {
                            val inputStream = applicationContext.contentResolver.openInputStream(uri)
                                    as FileInputStream
                            File(BaresipService.filesPath + "/ca_certs.crt")
                                .copyInputStreamToFile(inputStream)
                            inputStream.close()
                            Config.removeVariable("sip_cafile")
                            Config.addLine("sip_cafile ${BaresipService.filesPath}/ca_certs.crt")
                            save = true
                            restart = true
                        } catch (e: Error) {
                            Utils.alertView(this, getString(R.string.error),
                                getString(R.string.read_ca_certs_error))
                            caFile.isChecked = false
                        }
                    }
                else
                    caFile.isChecked = false
            }

        caFile.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT < 29) {
                    if (!Utils.requestPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            READ_CERT_PERMISSION_CODE)) {
                        caFile.isChecked = false
                        return@setOnCheckedChangeListener
                    }
                    val downloadsPath = Utils.downloadsPath("ca_certs.crt")
                    val content = Utils.getFileContents(downloadsPath)
                    if (content == null) {
                        Utils.alertView(this, getString(R.string.error),
                            getString(R.string.read_ca_certs_error))
                        caFile.isChecked = false
                        return@setOnCheckedChangeListener
                    }
                    val filesPath = BaresipService.filesPath + "/ca_certs.crt"
                    Utils.putFileContents(filesPath, content)
                    Config.removeVariable("sip_cafile")
                    Config.addLine("sip_cafile $filesPath")
                    save = true
                    restart = true
                } else {
                    Utils.selectInputFile(certificatesRequest)
                }
            } else {
                Config.removeVariable("sip_cafile")
                save = true
                restart = true
            }
        }

        val callVolSpinner = binding.VolumeSpinner
        val volKeys = arrayListOf("None", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        val volVals = arrayListOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val curVal = callVolume
        val curKey = volKeys[curVal]
        volKeys.removeAt(curVal)
        volVals.removeAt(curVal)
        volKeys.add(0, curKey)
        volVals.add(0, curVal)
        val callVolAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
                volKeys)
        callVolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        callVolSpinner.adapter = callVolAdapter
        callVolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                callVolume = volVals[volKeys.indexOf(parent.selectedItem.toString())]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }

        darkTheme = binding.DarkTheme
        oldDisplayTheme = Preferences(applicationContext).displayTheme
        darkTheme.isChecked = oldDisplayTheme == AppCompatDelegate.MODE_NIGHT_YES

        debug = binding.Debug
        val dbCv = Config.variable("log_level")
        oldLogLevel = if (dbCv.size == 0)
            "2"
        else
            dbCv[0]
        debug.isChecked =  oldLogLevel == "0"

        sipTrace = binding.SipTrace
        sipTrace.isChecked = BaresipService.sipTrace

        reset = binding.Reset
        reset.isChecked = false

        reset.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val titleView = View.inflate(this, R.layout.alert_title, null) as TextView
                titleView.text = getString(R.string.confirmation)
                with (AlertDialog.Builder(this@ConfigActivity)) {
                    setCustomTitle(titleView)
                    setMessage(getString(R.string.reset_config_alert))
                    setPositiveButton(getText(R.string.reset)) { dialog, _ ->
                        Config.reset(this@ConfigActivity)
                        save = false
                        restart = true
                        done()
                        dialog.dismiss()
                    }
                    setNegativeButton(getText(R.string.cancel)) { dialog, _ ->
                        reset.isChecked = false
                        dialog.dismiss()
                    }
                    show()
                }
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        super.onCreateOptionsMenu(menu)

        val inflater = menuInflater
        inflater.inflate(R.menu.check_icon, menu)

        this.menu = menu

        return true

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (BaresipService.activities.indexOf("config") == -1) return true

        when (item.itemId) {

            R.id.checkIcon -> {

                var autoStartString = "no"
                if (autoStart.isChecked) autoStartString = "yes"
                if (oldAutoStart != autoStartString) {
                    Config.replaceVariable("auto_start", autoStartString)
                    save = true
                    restart = false
                }

                val listenAddr = listenAddr.text.toString().trim()
                if (listenAddr != oldListenAddr) {
                    if ((listenAddr != "") && !Utils.checkIpPort(listenAddr)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_listen_address)}: $listenAddr")
                        return false
                    }
                    Config.removeVariable("sip_listen")
                    if (listenAddr != "") Config.addLine("sip_listen $listenAddr")
                    save = true
                    restart = true
                }

                val dnsServers = addMissingPorts(
                    dnsServers.text.toString().trim().lowercase(Locale.ROOT))
                if (dnsServers != oldDnsServers) {
                    if (!checkDnsServers(dnsServers)) {
                        Utils.alertView(this, getString(R.string.notice),
                                "${getString(R.string.invalid_dns_servers)}: $dnsServers")
                        return false
                    }
                    Config.removeVariable("dyn_dns")
                    Config.removeVariable("dns_server")
                    if (dnsServers.isNotEmpty()) {
                        for (server in dnsServers.split(","))
                            Config.addLine("dns_server $server")
                        Config.addLine("dyn_dns no")
                        if (Api.net_use_nameserver(dnsServers) != 0) {
                            Utils.alertView(this, getString(R.string.error),
                                    "${getString(R.string.failed_to_set_dns_servers)}: $dnsServers")
                            return false
                        }
                    } else {
                        Config.addLine("dyn_dns yes")
                        Config.updateDnsServers(BaresipService.dnsServers)
                    }
                    Api.net_debug()
                    save = true
                }

                if (verifyServer.isChecked && !caFile.isChecked) {
                    Utils.alertView(this, getString(R.string.error),
                            getString(R.string.verify_server_error))
                    verifyServer.isChecked = false
                    return false
                }

                val verifyServerString = if (verifyServer.isChecked) "yes" else "no"
                if (oldVerifyServer != verifyServerString) {
                    Config.replaceVariable("sip_verify_server", verifyServerString)
                    save = true
                    restart = true
                }

                if (BaresipService.callVolume != callVolume) {
                    BaresipService.callVolume = callVolume
                    Config.replaceVariable("call_volume", callVolume.toString())
                    save = true
                }

                val newDisplayTheme = if (darkTheme.isChecked)
                    AppCompatDelegate.MODE_NIGHT_YES
                else
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                if (oldDisplayTheme != newDisplayTheme)
                    Preferences(applicationContext).displayTheme = newDisplayTheme

                var logLevelString = "2"
                if (debug.isChecked) logLevelString = "0"
                if (oldLogLevel != logLevelString) {
                    Config.replaceVariable("log_level", logLevelString)
                    Api.log_level_set(logLevelString.toInt())
                    Log.logLevelSet(logLevelString.toInt())
                    save = true
                }

                BaresipService.sipTrace = sipTrace.isChecked
                Api.uag_enable_sip_trace(sipTrace.isChecked)

                done()

            }

            android.R.id.home -> {

                BaresipService.activities.remove("config")
                setResult(Activity.RESULT_CANCELED, Intent(this, MainActivity::class.java))
                finish()

            }
        }

        return true

    }

    private fun done() {

        if (save)
            Config.save()
        BaresipService.activities.remove("config")
        val intent = Intent(this, MainActivity::class.java)
        if (restart)
            intent.putExtra("restart", true)
        setResult(RESULT_OK, intent)
        finish()

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            READ_CERT_PERMISSION_CODE ->
                if (grantResults.isNotEmpty() &&
                    (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                        menu!!.performIdentifierAction(R.id.checkIcon, 0)
                else
                    certificateFile.isChecked = false
            READ_CA_PERMISSION_CODE ->
                if (grantResults.isNotEmpty() &&
                    (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                        menu!!.performIdentifierAction(R.id.checkIcon, 0)
                else
                    caFile.isChecked = false
        }

    }

    override fun onBackPressed() {

        BaresipService.activities.remove("config")
        setResult(Activity.RESULT_CANCELED, Intent(this, MainActivity::class.java))
        finish()
        super.onBackPressed()

    }

    fun onClick(v: View) {
        when (v) {
            binding.AutoStartTitle -> {
                Utils.alertView(this, getString(R.string.start_automatically),
                        getString(R.string.start_automatically_help))
            }
            binding.ListenAddressTitle -> {
                Utils.alertView(this, getString(R.string.listen_address),
                        getString(R.string.listen_address_help))
            }
            binding.DnsServersTitle  -> {
                Utils.alertView(this, getString(R.string.dns_servers),
                        getString(R.string.dns_servers_help))
            }
            binding.CertificateFileTitle  -> {
                Utils.alertView(this, getString(R.string.tls_certificate_file),
                        getString(R.string.tls_certificate_file_help))
            }
            binding.VerifyServerTitle  -> {
                Utils.alertView(this, getString(R.string.verify_server),
                        getString(R.string.verify_server_help))
            }
            binding.CAFileTitle -> {
                Utils.alertView(this, getString(R.string.tls_ca_file),
                        getString(R.string.tls_ca_file_help))
            }
            binding.AudioTitle  -> {
                startActivity(Intent(this, AudioActivity::class.java))
            }
            binding.VolumeTitle -> {
                Utils.alertView(this, getString(R.string.default_call_volume),
                        getString(R.string.default_call_volume_help))
            }
            binding.DarkThemeTitle -> {
                Utils.alertView(this, getString(R.string.dark_theme),
                        getString(R.string.dark_theme_help))
            }
            binding.DebugTitle -> {
                Utils.alertView(this, getString(R.string.debug), getString(R.string.debug_help))
            }
            binding.SipTraceTitle -> {
                Utils.alertView(this, getString(R.string.sip_trace),
                        getString(R.string.sip_trace_help))
            }
            binding.ResetTitle -> {
                Utils.alertView(this, getString(R.string.reset_config),
                        getString(R.string.reset_config_help))
            }
        }
    }

    private fun checkDnsServers(dnsServers: String): Boolean {
        if (dnsServers.isEmpty()) return true
        for (server in dnsServers.split(","))
            if (!Utils.checkIpPort(server.trim())) return false
        return true
    }

    private fun addMissingPorts(addressList: String): String {
        if (addressList == "") return ""
        var result = ""
        for (addr in addressList.split(","))
            result = if (Utils.checkIpPort(addr)) {
                "$result,$addr"
            } else {
                if (Utils.checkIpV4(addr))
                    "$result,$addr:53"
                else
                    "$result,[$addr]:53"
            }
        return result.substring(1)
    }

}

