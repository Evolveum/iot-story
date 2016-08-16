package com.evolveum.smartwidgets.swth;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.Properties;


/**
 * @author mederly
 */
public class Thermometer {

	private static final File COMM_PROPERTIES_FILE = new File("communication.properties");
	private static final String SERIAL_NUMBER = "serialNumber";
	private static final String REGISTRATION_KEY = "registrationKey";
	private static final String MIDPOINT_HOST_PORT = "midPointHostPort";
	private static final String CITY = "city";
	private static final String OPENWEATHERMAP_API_KEY = "openWeatherMapApiKey";
	private static final String PERIOD = "period";

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			run();
		} else if (args.length == 1 && "configure".equals(args[0])) {
			configure();
		} else {
			System.out.println("Usage:\n");
			System.out.println("java -jar sw-thermometer.jar configure       Configures the 'device'");
			System.out.println("java -jar sw-thermometer.jar                 Starts the 'device'");
		}
	}

	private static void configure() throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		Properties comm = getCommunicationProperties();

		for (;;) {
			System.out.print("Registration key [" + comm.get(REGISTRATION_KEY) + "]: ");
			String key = br.readLine();
			if (StringUtils.isBlank(key)) {
				break;
			} else if (key.length() != 17 || key.charAt(8) != '-') {
				System.out.println("Invalid key, please re-enter.");
			} else {
				comm.put(REGISTRATION_KEY, key);
				break;
			}
		}

		System.out.print("MidPoint host:port [" + comm.get(MIDPOINT_HOST_PORT) + "]: ");
		String hostPort = br.readLine();
		if (StringUtils.isNotBlank(hostPort)) {
			comm.put(MIDPOINT_HOST_PORT, hostPort);
		}

		System.out.print("OpenWeatherMap API key (if empty or 'X', the data will be randomly generated) [" + comm.get(OPENWEATHERMAP_API_KEY) + "]: ");
		String apiKey = br.readLine();
		if (StringUtils.isNotBlank(apiKey)) {
			comm.put(OPENWEATHERMAP_API_KEY, apiKey);
		}

		if (useOpenWeatherMap((String) comm.get(OPENWEATHERMAP_API_KEY))) {
			System.out.print("City to monitor (if empty or 'X', the data will be randomly generated) [" + comm.get(CITY) + "]: ");
			String city = br.readLine();
			if (StringUtils.isNotBlank(city)) {
				comm.put(CITY, city);
			}
		}

		for (;;) {
			System.out.print("Data collection period in seconds [" + comm.get(PERIOD) + "]: ");
			String p = br.readLine();
			if (StringUtils.isBlank(p)) {
				break;
			} else if (!StringUtils.isNumeric(p)) {
				System.out.println("Invalid period, please re-enter.");
			} else {
				comm.put(PERIOD, p);
				break;
			}
		}

		FileWriter writer = new FileWriter(COMM_PROPERTIES_FILE);
		comm.store(writer, "");
		writer.close();
		System.out.println("Settings written to " + COMM_PROPERTIES_FILE);
	}

	private static Properties getCommunicationProperties() throws IOException {
		Properties comm = new Properties();
		comm.put(REGISTRATION_KEY, "");
		comm.put(MIDPOINT_HOST_PORT, "localhost:8080");
		comm.put(OPENWEATHERMAP_API_KEY, "");
		comm.put(CITY, "");
		comm.put(PERIOD, "60");

		if (COMM_PROPERTIES_FILE.exists()) {
			FileReader reader = new FileReader(COMM_PROPERTIES_FILE);
			comm.load(reader);
			reader.close();
		}

		if (!comm.containsKey(SERIAL_NUMBER)) {
			comm.put(SERIAL_NUMBER, String.valueOf(Math.round(Math.random() * 10e15)));
		}

		return comm;
	}

	private static boolean useOpenWeatherMap(String apiKey) {
		return StringUtils.isNotBlank(apiKey) && !apiKey.equalsIgnoreCase("X");
	}

	private static void run() throws IOException {
		register();
		monitor();
	}

	private static void register() throws IOException {
		Properties comm = getCommunicationProperties();
		String regKey = (String) comm.get(REGISTRATION_KEY);
		if (StringUtils.isBlank(regKey) || regKey.length() != 17) {
			System.out.println("Missing or invalid midPoint registration key, exiting.");
			System.exit(0);
		}
		String serialNumber = (String) comm.get(SERIAL_NUMBER);

		Client client = ClientBuilder.newClient();

		String endpoint = "http://" + comm.get(MIDPOINT_HOST_PORT) + "/midpoint/ws/iot/devices/" + serialNumber;
		WebTarget target = client.target(endpoint).queryParam("key", regKey.substring(0, 8));
		Invocation.Builder builder = target.request(MediaType.TEXT_PLAIN);
		Invocation invocation = builder.buildGet();

		System.out.println("Invoking " + endpoint);
		Response response = invocation.invoke();
		System.out.println("Response: " + IOUtils.toString((InputStream) response.getEntity()));
		System.exit(0);
	}

	private static void monitor() {
		//		Client client = ClientBuilder.newClient().register(JacksonJsonProvider.class);
		//		WebTarget target = client.target("http://api.openweathermap.org/data/2.5/weather").queryParam("q", "Levice").queryParam("APPID", "bd8aa70a942f7533d6f44545bc679be3");
		//		Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON_TYPE);
		//		Response response = builder.get();  // Successful
		//		Weather w = response.readEntity(Weather.class);
		//		System.out.println(w.getMain().getTemp() - 273.16);
		//		System.out.println(w.getName());
		//		System.out.println(w.getCod());

	}
}
