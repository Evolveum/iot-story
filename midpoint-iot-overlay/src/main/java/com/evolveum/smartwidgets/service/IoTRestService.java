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
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
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

	private String findOrRegisterDevice(String serialNumber, String registrationKey, Task task, OperationResult result) {
		return "Hello " + serialNumber + "!";
	}

}

