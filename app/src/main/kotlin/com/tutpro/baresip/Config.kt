package com.tutpro.baresip

import android.content.Context

import java.net.InetAddress
import java.nio.charset.StandardCharsets

object Config {

    private val configPath = BaresipService.filesPath + "/config"
    private var config = String(Utils.getFileContents(configPath)!!, StandardCharsets.ISO_8859_1)

    fun initialize() {

        Log.d(TAG, "Config is '$config'")

        if (!config.contains(Regex("ausrc_format s16"))) {
            config = "${config}ausrc_format s16\nauplay_format s16\nauenc_format s16\naudec_format s16\nmodule webrtc_aec.so\n"
        }

        if (config.contains(Regex("#module_app[ ]+mwi.so"))) {
            config = config.replace(Regex("#module_app[ ]+mwi.so"),
                    "module_app mwi.so")
        }

        if (!config.contains("opus_application")) {
            config = "${config}opus_application voip\n"
        }

        if (!config.contains("opus_samplerate")) {
            config = "${config}opus_samplerate 16000\n"
            val accountsPath = BaresipService.filesPath + "/accounts"
            var accounts = String(Utils.getFileContents(accountsPath)!!, StandardCharsets.ISO_8859_1)
            accounts = accounts.replace("opus/48000/1", "opus/16000/1")
            Utils.putFileContents(accountsPath, accounts.toByteArray())
        }

        if (!config.contains("opus_stereo")) {
            config = "${config}opus_stereo no\n"
        }

        if (!config.contains("opus_sprop_stereo")) {
            config = "${config}opus_sprop_stereo no\n"
        }

        if (!config.contains("webrtc_aec_extended_filter")) {
            config = "${config}webrtc_aec_extended_filter yes\n"
        }

        if (!config.contains("log_level")) {
            config = "${config}log_level 2\n"
            Log.logLevel = Log.LogLevel.WARN
            BaresipService.logLevel = 2
        } else {
            val ll = variable("log_level")[0].toInt()
            replaceVariable("log_level", "$ll")
            Log.logLevelSet(ll)
            BaresipService.logLevel = ll
        }

        if (config.contains("net_interface")) {
           BaresipService.netInterface = variable("net_interface")[0]
        }

        if (!config.contains("call_volume")) {
            config = "${config}call_volume 0\n"
        } else {
            BaresipService.callVolume = variable("call_volume")[0].toInt()
        }

        if (!config.contains("dyn_dns")) {
            config = "${config}dyn_dns no\n"
        } else {
            if (config.contains(Regex("dyn_dns[ ]+yes"))) {
                removeVariable("dns_server")
                for (dnsServer in BaresipService.dnsServers)
                    config = if (Utils.checkIpV4(dnsServer.hostAddress))
                        "${config}dns_server ${dnsServer.hostAddress}:53\n"
                    else
                        "${config}dns_server [${dnsServer.hostAddress}]:53\n"
                BaresipService.dynDns = true
            }
        }

        if (!config.contains("jitter_buffer_type")) {
            config = "${config}jitter_buffer_type adaptive\n"
            config = "${config}jitter_buffer_wish 6\n"
        }

        removeLine("module ilbc.so")

        Utils.putFileContents(configPath, config.toByteArray())
        BaresipService.isConfigInitialized = true
        Log.i(TAG, "Initialized config to '$config'")

    }

    fun variable(name: String): ArrayList<String> {
        val result = ArrayList<String>()
        val lines = config.split("\n")
        for (line in lines) {
            if (line.startsWith(name))
                result.add((line.substring(name.length).trim()).split("# \t")[0])
        }
        return result
    }

    fun addLine(line: String) {
        config += "$line\n"
    }

    fun removeLine(line: String) {
        config = Utils.removeLinesStartingWithString(config, line)
    }

    fun addModuleLine(line: String) {
        // Make sure it goes before first 'module_tmp'
        config = config.replace("module opensles.so", "$line\nmodule opensles.so")
    }

    fun removeVariable(variable: String) {
        config = Utils.removeLinesStartingWithString(config, "$variable ")
    }

    fun replaceVariable(variable: String, value: String) {
        removeVariable(variable)
        addLine("$variable $value")
    }

    fun reset(ctx: Context) {
        Utils.copyAssetToFile(ctx, "config", configPath)
    }

    fun save() {
        var result = ""
        for (line in config.split("\n"))
            if (line.isNotEmpty())
                result = result + line + '\n'
        config = result
        Utils.putFileContents(configPath, config.toByteArray())
        Log.d(TAG, "Saved new config '$result'")
        // Api.reload_config()
    }

    fun updateDnsServers(dnsServers: List<InetAddress>): Int {
        Log.i(TAG, "Updating dnsServers with $dnsServers")
        var servers = ""
        for (dnsServer in dnsServers) {
            var address = dnsServer.hostAddress.removePrefix("/")
            address = if (Utils.checkIpV4(address))
                "${address}:53"
            else
                "[${address}]:53"
            servers = if (servers == "")
                address
            else
                "${servers},${address}"
        }
        return Api.net_use_nameserver(servers)
    }

}
