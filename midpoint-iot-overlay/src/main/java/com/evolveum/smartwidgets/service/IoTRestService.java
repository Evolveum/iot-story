/**
 * Copyright (c) 2014-2016 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.smartwidgets.service;

import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.impl.security.SecurityHelper;
import com.evolveum.midpoint.model.impl.util.RestServiceUtil;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.security.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.evolveum.smartwidgets.Constants;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author mederly
 */

@Service
@Produces({"text/plain"})
public class IoTRestService {

	private static final Trace LOGGER = TraceManager.getTrace(IoTRestService.class);

	private static final String OPERATION_FIND_OR_REGISTER_DEVICE = IoTRestService.class.getName() + ".findOrRegisterDevice";

	@Autowired
	private TaskManager taskManager;

	@Autowired
	private ModelService modelService;

	@Autowired
	private RepositoryService repositoryService;

	@Autowired
	private SecurityHelper securityHelper;

	@Autowired
	private SecurityEnforcer securityEnforcer;

	@Autowired
	private PrismContext prismContext;

	@GET
	@Path("/devices/{serialNumber}")
	public Response findOrRegisterDevice(@PathParam("serialNumber") String serialNumber, @QueryParam("key") String lookupKey, @Context MessageContext mc) {

		LOGGER.info("findOrRegisterDevice called with serialNumber = {} and lookupKey = {}", serialNumber, lookupKey);

		Task task = taskManager.createTaskInstance(OPERATION_FIND_OR_REGISTER_DEVICE);
		OperationResult result = task.getResult();

		Response response;
		try {
			PrismObject<UserType> administrator = repositoryService.getObject(UserType.class, SystemObjectsType.USER_ADMINISTRATOR.value(), null, result);
			securityEnforcer.setupPreAuthenticatedSecurityContext(administrator);
			task.setOwner(administrator);

			String retval = findOrRegisterDevice(serialNumber, lookupKey, task, result);
			response = Response.ok().entity(retval).build();
		} catch (CommonException|RuntimeException e) {
			response = RestServiceUtil.handleException(e);
		}

		result.computeStatus();
		RestServiceUtil.finishRequest(task, securityHelper);
		return response;
	}

	private String findOrRegisterDevice(String serialNumber, String lookupKey, Task task, OperationResult result) throws CommonException {

		// Find the customer (org)

		ObjectQuery orgQuery = QueryBuilder.queryFor(OrgType.class, prismContext)
				.item(OrgType.F_ORG_TYPE).eq("customer")
				.and().item(OrgType.F_EXTENSION, Constants.REGISTRATION_LOOKUP_KEY_QNAME).eq(lookupKey)
				.build();

		List<PrismObject<OrgType>> orgs = modelService.searchObjects(OrgType.class, orgQuery, null, task, result);
		if (orgs.isEmpty()) {
			throw new ObjectNotFoundException("No organization exists for lookupKey = " + lookupKey);
		} else if (orgs.size() > 1) {
			throw new IllegalStateException("More than one organization found for lookupKey = " + lookupKey + ": " + orgs);
		}

		OrgType customer = orgs.get(0).asObjectable();
		LOGGER.info("Customer: {}", ObjectTypeUtil.toShortString(customer));

		// Find the ThingSpeak resource

		ObjectQuery resourceQuery = QueryBuilder.queryFor(ResourceType.class, prismContext)
				.isDirectChildOf(customer.getOid()).build();
		List<PrismObject<ResourceType>> resources = modelService.searchObjects(ResourceType.class, resourceQuery,
				GetOperationOptions.createNoFetchCollection(), task, result);
		if (resources.isEmpty()) {
			throw new ObjectNotFoundException("No resource exists for customer " + customer);
		} else if (resources.size() > 1) {
			throw new IllegalStateException("More than one organization found for customer " + customer + ": " + resources);
		}

		ResourceType resource = resources.get(0).asObjectable();
		LOGGER.info("Resource found: {}", ObjectTypeUtil.toShortString(resource));

		// Find the service (if exists)

		ObjectQuery serviceQuery = QueryBuilder.queryFor(ServiceType.class, prismContext)
				.item(ServiceType.F_NAME).eqPoly(serialNumber)
				.build();
		List<PrismObject<ServiceType>> services = modelService.searchObjects(ServiceType.class, serviceQuery,
				null, task, result);
		if (services.size() > 1) {
			throw new IllegalStateException("More than one service found for serial number " + serialNumber + ": " + services);
		}

		ServiceType service;
		if (services.isEmpty()) {
			service = createService(serialNumber, customer, resource, task, result);
		} else {
			service = services.get(0).asObjectable();
		}

		if (service != null && service.getIdentifier() != null) {
			return "channel=" + service.getIdentifier() + "\n" +
					"writeKey=" + service.asPrismObject().findProperty(Constants.CHANNEL_WRITE_KEY_PATH).getAnyRealValue() + "\n";
		} else {
			throw new IllegalStateException("Device object could not be created");
		}
	}

	private ServiceType createService(String serialNumber, OrgType customer, ResourceType resource, Task task, OperationResult result) throws CommonException {
		ServiceType service = prismContext.createObject(ServiceType.class).asObjectable();
		service.setName(new PolyStringType(serialNumber));
		service.setDisplayName(new PolyStringType("Thermometer " + serialNumber));
		service.getAssignment().add(ObjectTypeUtil.createAssignmentTo(customer.asPrismObject()));
		AssignmentType resourceAssignment = ObjectTypeUtil.createAssignmentTo(resource.asPrismObject());
		resourceAssignment.getConstruction().setKind(ShadowKindType.GENERIC);
		resourceAssignment.getConstruction().setIntent("channel");
		service.getAssignment().add(resourceAssignment);

		@SuppressWarnings("unchecked")
		List<ObjectDelta<? extends ObjectType>> deltas = Collections.<ObjectDelta<? extends ObjectType>>singletonList(ObjectDelta.createAddDelta(service.asPrismObject()));
		Collection<ObjectDeltaOperation<? extends ObjectType>> objectDeltaOperations = modelService.executeChanges(deltas, null, task, result);

		for (ObjectDeltaOperation odo : objectDeltaOperations) {
			if (odo.getObjectDelta().getObjectToAdd() != null && odo.getObjectDelta().getObjectToAdd().asObjectable() instanceof ServiceType) {
				String oid = odo.getObjectDelta().getObjectToAdd().getOid();
				return modelService.getObject(ServiceType.class, oid, null, task, result).asObjectable();
			}
		}
		return null;
	}

}

