package nxt.upnp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.xml.sax.SAXException;

import nxt.Constants;
import nxt.Nxt;
import nxt.util.Logger;
import nxt.util.ThreadPool;

public class UPnP {
	private static final ExecutorService uPnPService = Executors
			.newFixedThreadPool(1);
	private static GatewayDiscover gatewayDiscover = new GatewayDiscover();
	private static GatewayDevice activeGW;
	static final int TESTNET_PEER_PORT = 9874;
	static final int internalPort = Constants.isTestnet ? TESTNET_PEER_PORT
			: Nxt.getIntProperty("nxt.peerServerPort");
	private static final String myAddress = Nxt
			.getStringProperty("nxt.myAddress");
	private static final String externalIP = getExternalIp();

	private static int externalPort = getExternalPort();
	private static Runnable generateUPnPMappingsThread;

	static {
		generateUPnPMappingsThread = null;

		if (Nxt.getBooleanProperty("nxt.upnp")) {			
			InetAddress peerServerAddress = null;
			try {
				peerServerAddress = java.net.InetAddress.getByName(Nxt
						.getStringProperty("nxt.peerServerHost"));
			} catch (UnknownHostException e1) {

			}
			if (myAddress != null
					&& externalIP != null
					&& peerServerAddress != null
					&& (peerServerAddress.isAnyLocalAddress() || !(peerServerAddress
							.isLoopbackAddress() || peerServerAddress
							.isMulticastAddress()))) {
				
				generateUPnPMappingsThread = new Runnable() {

					@Override
					public void run() {
						// check connection and if necessary map again
						try {
							main();
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

				};
				Logger.logInfoMessage("UPnP support enabled");
			} else {
				Logger.logWarningMessage("You need an announce address to use UPnP! UPnP disabled");
			}
		}
	}

	static {
		if (generateUPnPMappingsThread != null)
			ThreadPool.scheduleThread("generateUPnPMappings",generateUPnPMappingsThread,
					Nxt.getIntProperty("nxt.upnpRetry"), TimeUnit.SECONDS);
	}

	public static void init() {
	}

	public static void shutdown() {
		if (generateUPnPMappingsThread == null)
			return;
		try {
			activeGW.deletePortMapping(externalPort, "TCP");
			Logger.logDebugMessage("UPnP mapping removed.");
		} catch (IOException | SAXException e) {
			// TODO Auto-generated catch block
			Logger.logDebugMessage("Could not remove UPnP mapping.");
		}
		ThreadPool.shutdownExecutor(uPnPService);
	}

	private static boolean main() throws Exception {

		Logger.logDebugMessage("Looking for Gateway Devices...");

		// get gateway with same public ip as announced
		
		activeGW = getGateway(externalIP);
		if (activeGW == null)
			return false;

		// check whether port is already mapped

		InetAddress localAddress = activeGW.getLocalAddress();
		Logger.logDebugMessage("Using local address: "
				+ localAddress.getHostAddress());
		
		// create portmap for mapping look up
		PortMappingEntry portMapping = new PortMappingEntry();
		portMapping.setRemoteHost(externalIP);
		portMapping.setExternalPort(externalPort);
		portMapping.setProtocol("TCP");

		if (activeGW.getGenericPortMappingEntry(0, portMapping)) {
			if (portMapping.getInternalClient().equals(
					activeGW.getLocalAddress().getHostAddress())
					&& portMapping.getInternalPort() == internalPort) {
				return true;
			} else {
				activeGW.deletePortMapping(externalPort, "TCP");
			}
		}

		if (activeGW.addPortMapping(externalPort, internalPort,
				localAddress.getHostAddress(), "TCP", "FSM")) {
			Logger.logMessage("UPnP :" + activeGW.getExternalIPAddress() + ":"
					+ externalPort + " mapped to "
					+ localAddress.getHostAddress() + ":" + internalPort);
			return true;
		}
		return false;
	}

	private static GatewayDevice getGateway(String externalGwIp)
			throws Exception {
		Map<InetAddress, GatewayDevice> gateways = gatewayDiscover.discover();

		for (GatewayDevice gatewayDevice : gateways.values()) {
			if (gatewayDevice.getExternalIPAddress().equals(externalGwIp))
				return gatewayDevice;
		}

		return null;
	}

	/**
	 * get corresponding external IP from myAddress
	 * 
	 * @return Returns the IP address as String; or null if IP address is not a
	 *         public IP address; or null if an IP address could not extracted
	 *         from myAddress
	 */
	private static String getExternalIp() {

		if (myAddress == null)
			return null;

		InetAddress myInetAddress;
		try {
			final URI myAddressUri = new URI("http://" + myAddress.trim());
			String host = myAddressUri.getHost();

			myInetAddress = java.net.InetAddress.getByName(host);

			if (myInetAddress.isAnyLocalAddress()
					|| myInetAddress.isLoopbackAddress()
					|| myInetAddress.isSiteLocalAddress())
				return null;

			return myInetAddress.getHostAddress();

		} catch (UnknownHostException | URISyntaxException e) {
			Logger.logWarningMessage("Your announce address is invalid: " + myAddress);
			return null;
		}
	}

	private static int getExternalPort() {

		if (myAddress == null)
			return internalPort; // fallback: external port same as internal port

		int announcedPort;
		
		try {
			final URI myAddressUri = new URI("http://" + myAddress.trim());
			announcedPort = myAddressUri.getPort();
		} catch (URISyntaxException use) {
			// should never happen
			Logger.logWarningMessage("Your announce address is invalid: " + myAddress);
			return -1;
		}

		if (!(announcedPort >= 0 && announcedPort < 65536))
			return internalPort; // fallback: external port same as internal port

		return announcedPort;
	}
}
