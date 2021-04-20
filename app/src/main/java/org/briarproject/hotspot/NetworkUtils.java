package org.briarproject.hotspot;


import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.list;

class NetworkUtils {

    @Nullable
    static InetAddress getAccessPointAddress() {
        for (NetworkInterface i: getNetworkInterfaces()) {
            if (i.getName().startsWith("p2p")) {
                for (InterfaceAddress a : i.getInterfaceAddresses()) {
                    if (a.getAddress().getAddress().length == 4) return a.getAddress();
                }
            }
        }
        return null;
    }

    static String getNetworkInterfaceSummary() {
        StringBuilder sb = new StringBuilder();
        for (NetworkInterface i: getNetworkInterfaces()) {
            sb.append(i.getName()).append(":");
            for (InterfaceAddress a : i.getInterfaceAddresses()) {
                if (a.getAddress().getAddress().length <= 4) {
                    sb.append(" ").append(a.getAddress());
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    static List<NetworkInterface> getNetworkInterfaces() {
        try {
            Enumeration<NetworkInterface> ifaces =
                    NetworkInterface.getNetworkInterfaces();
            return ifaces == null ? emptyList() : list(ifaces);
        } catch (SocketException e) {
            e.printStackTrace();
            return emptyList();
        }
    }

}
