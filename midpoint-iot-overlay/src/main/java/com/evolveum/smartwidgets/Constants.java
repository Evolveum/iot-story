package com.evolveum.smartwidgets;

import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import javax.xml.namespace.QName;

/**
 * @author mederly
 */
public class Constants {

	public static final String EXT_NS = "http://smartwidgets-r-us.com/xml/ns/midpoint/schema/extension-3";

	public static final QName THINGSPEAK_API_KEY_QNAME = new QName(EXT_NS, "thingSpeakApiKey");
	public static final ItemPath THINGSPEAK_API_KEY_PATH = new ItemPath(ObjectType.F_EXTENSION, THINGSPEAK_API_KEY_QNAME);

	public static final QName REGISTRATION_KEY_QNAME = new QName(EXT_NS, "registrationKey");
	public static final ItemPath REGISTRATION_KEY_PATH = new ItemPath(ObjectType.F_EXTENSION, REGISTRATION_KEY_QNAME);

	public static final QName REGISTRATION_LOOKUP_KEY_QNAME = new QName(EXT_NS, "registrationLookupKey");
	public static final ItemPath REGISTRATION_LOOKUP_KEY_PATH = new ItemPath(ObjectType.F_EXTENSION, REGISTRATION_LOOKUP_KEY_QNAME);

	public static final QName CHANNEL_READ_KEY_QNAME = new QName(EXT_NS, "readKey");
	public static final ItemPath CHANNEL_READ_KEY_PATH = new ItemPath(ObjectType.F_EXTENSION, CHANNEL_READ_KEY_QNAME);

	public static final QName CHANNEL_WRITE_KEY_QNAME = new QName(EXT_NS, "writeKey");
	public static final ItemPath CHANNEL_WRITE_KEY_PATH = new ItemPath(ObjectType.F_EXTENSION, CHANNEL_WRITE_KEY_QNAME);

	public static final String RESOURCE_NAMESPACE = "http://midpoint.evolveum.com/xml/ns/public/connector/icf-1/bundle/org.forgerock.openicf.connectors.scriptedrest-connector/org.forgerock.openicf.connectors.scriptedrest.ScriptedRESTConnector";

	public static final String CUSTOMERS_ROOT_OID = "b2d0e5cd-5912-448f-a550-fed22ecbf059";
	public static final String RESOURCE_TEMPLATE_OID = "275ea738-5bb6-40ce-96fb-5941dbbc6dd5";

	public static final String USER_OBJECT_TEMPLATE_OID = "8ac75a6b-f87c-4c42-8e6d-f7376d2bbb68";
	public static final String ORG_OBJECT_TEMPLATE_OID = "2be09086-33a4-47f4-ae58-83614f4c8f94";
}
