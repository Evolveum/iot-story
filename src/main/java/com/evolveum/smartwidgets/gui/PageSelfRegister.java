/*
 * Copyright (c) 2012-2016 Biznet, Evolveum
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
package com.evolveum.smartwidgets.gui;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.Producer;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import com.evolveum.smartwidgets.Constants;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.RequiredTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.Model;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.Collections;

@PageDescriptor(url = "/selfregister")
public class PageSelfRegister extends PageBase {
	private static final long serialVersionUID = 1L;
	
	private static final String ID_FORM = "form";
	private static final String ID_EMAIL = "email";
	private static final String ID_ORGANIZATION = "organization";
	private static final String ID_CHOSEN_PASSWORD ="chosenPassword";
	private static final String ID_REPEAT_PASSWORD ="repeatPassword";
	private static final String ID_GIVEN_NAME ="givenName";
	private static final String ID_FAMILY_NAME ="familyName";
	private static final String ID_THINGSPEAK_API_KEY ="thingSpeakApiKey";

	private static final String DOT_CLASS = com.evolveum.midpoint.web.page.forgetpassword.PageForgetPassword.class.getName() + ".";
	private static final String OPERATION_IS_NAME_USED = DOT_CLASS + "isNameUsed";
	private static final String OPERATION_CREATE_OBJECTS = DOT_CLASS + "createObjects";

	private static final Trace LOGGER = TraceManager.getTrace(PageSelfRegister.class);

	public PageSelfRegister() {
		super();
		initLayout();
	}

	@Override
	protected void createBreadcrumb() {
		//don't create breadcrumb for this page
	}
	
	private void initLayout() {
		Form form = new Form(ID_FORM) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				String email = getValue(ID_EMAIL);
				try {
					String givenName = getValue(ID_GIVEN_NAME);
					String familyName = getValue(ID_FAMILY_NAME);
					String organization = getValue(ID_ORGANIZATION);
					if (StringUtils.isBlank(organization)) {
						organization = givenName + " " + familyName + "'s home";
					}
					String password1 = getValue(ID_CHOSEN_PASSWORD);
					String password2 = getValue(ID_REPEAT_PASSWORD);
					String thingSpeakApiKey = getValue(ID_THINGSPEAK_API_KEY);

					if (!StringUtils.equals(password1, password2)) {
						error("Passwords do not match.");
						return;
					}

					LOGGER.info("Registering {} ({} {}) with org {}", email, givenName, familyName, organization);

					if (isNameUsed(OrgType.class, email)) {
						error("Organization with the name '" + email + "' already exists.");
					} else if (isNameUsed(UserType.class, email)) {
						error("User with the email address '" + email + "' already exists.");
					} else {
						createObjects(email, organization, password1, givenName, familyName, thingSpeakApiKey);
					}
				} catch (RuntimeException e) {
					LoggingUtils.logUnexpectedException(LOGGER, "Coulnd't register the customer {}", e, email);
					error("Couldn't register the customer: " + e.getMessage());
				}
			}

			@SuppressWarnings("unchecked")
			private String getValue(String id) {
				return ((TextField<String>) get(id)).getModelObject();
			}
		};

		form.add(new RequiredTextField<>(ID_EMAIL, new Model<String>()));
		form.add(new TextField<>(ID_ORGANIZATION, new Model<String>()));
		form.add(new PasswordTextField(ID_CHOSEN_PASSWORD, new Model<String>()));
		form.add(new PasswordTextField(ID_REPEAT_PASSWORD, new Model<String>()));
		form.add(new RequiredTextField<>(ID_GIVEN_NAME, new Model<String>()));
		form.add(new RequiredTextField<>(ID_FAMILY_NAME, new Model<String>()));
		form.add(new RequiredTextField<>(ID_THINGSPEAK_API_KEY, new Model<String>()));

		add(form);
	}

	private Object createObjects(final String email, final String organization, final String password, final String givenName,
			final String familyName, final String thingSpeakApiKey) {
		return runPrivileged(new Producer<Object>() {
			@Override
			public Object run() {
				Task task = createAnonymousTask(OPERATION_CREATE_OBJECTS);
				try {
					OrgType org = createOrg(email, organization, thingSpeakApiKey, task);
					createUser(email, givenName, familyName, password, org, task);
					createResource(org, task);
				} catch (CommonException|EncryptionException e) {
					throw new SystemException(e);
				}
				return null;
			}
		});
	}

	private OrgType createOrg(String email, String organization, String thingSpeakApiKey, Task task) throws CommonException {
		OrgType org = new OrgType(getPrismContext());
		org.setName(PolyStringType.fromOrig(email));
		org.setDisplayName(PolyStringType.fromOrig(organization));
		org.getOrgType().add("customer");
		org.getAssignment().add(ObjectTypeUtil.createAssignmentTo(
				ObjectTypeUtil.createObjectRef(Constants.CUSTOMERS_ROOT_OID, ObjectTypes.ORG)));

		String registrationKey = generateRegistrationKey();
		org.asPrismObject().findOrCreateProperty(Constants.THINGSPEAK_API_KEY_PATH).setRealValue(thingSpeakApiKey);
		org.asPrismObject().findOrCreateProperty(Constants.REGISTRATION_KEY_PATH).setRealValue(registrationKey);
		org.asPrismObject().findOrCreateProperty(Constants.REGISTRATION_LOOKUP_KEY_PATH).setRealValue(registrationKey.substring(0, 8));

		addObject(org, task);
		return org;
	}

	// TODO move to some "util" class to be reusable (when regenerating the registration key)
	private String generateRegistrationKey() {
		return (RandomStringUtils.randomAlphanumeric(8) + "-" + RandomStringUtils.randomAlphanumeric(8)).toUpperCase();
	}

	private UserType createUser(String email, String givenName, String familyName, String password, OrgType org, Task task)
			throws CommonException, EncryptionException {
		UserType user = new UserType(getPrismContext());
		user.setName(PolyStringType.fromOrig(email));
		user.setGivenName(PolyStringType.fromOrig(givenName));
		user.setFamilyName(PolyStringType.fromOrig(familyName));
		user.setFullName(PolyStringType.fromOrig(givenName + " " + familyName));
		user.setEmailAddress(email);
		user.setCredentials(createCredentials(password));
		// assignments
		user.getAssignment().add(ObjectTypeUtil.createAssignmentTo(org.asPrismObject()));
		AssignmentType managingAssignment = ObjectTypeUtil.createAssignmentTo(org.asPrismObject());
		managingAssignment.getTargetRef().setRelation(SchemaConstants.ORG_MANAGER);
		user.getAssignment().add(managingAssignment);
		// execution
		addObject(user, task);
		return user;
	}

	@NotNull
	private CredentialsType createCredentials(String password) throws EncryptionException {
		CredentialsType credentials = new CredentialsType(getPrismContext());
		PasswordType passwordType = new PasswordType();
		passwordType.setValue(getMidpointApplication().getProtector().encryptString(password));
		credentials.setPassword(passwordType);
		return credentials;
	}

	private ResourceType createResource(OrgType org, Task task) throws CommonException, EncryptionException {
		ResourceType template = getModelService().getObject(ResourceType.class, Constants.RESOURCE_TEMPLATE_OID, null, task, task.getResult()).asObjectable();

		ResourceType resource = new ResourceType(getPrismContext());
		resource.asPrismObject().setDefinition(template.asPrismObject().getDefinition().clone());
		resource.setName(PolyStringType.fromOrig("ThingSpeak channels of " + PolyStringType.fromOrig(org.getName().getOrig())));
		resource.setConnectorRef(template.getConnectorRef().clone());
		resource.setConnectorConfiguration(template.getConnectorConfiguration().clone());
		resource.setSchemaHandling(template.getSchemaHandling().clone());
		resource.setCapabilities(template.getCapabilities().clone());
		resource.setSynchronization(template.getSynchronization().clone());

		// password
		ItemPath passwordPath = new ItemPath(
				ResourceType.F_CONNECTOR_CONFIGURATION,
				SchemaConstants.ICF_CONFIGURATION_PROPERTIES,
				new QName(Constants.RESOURCE_NAMESPACE, "password"));
		String apiKey = (String) org.asPrismObject().findProperty(Constants.THINGSPEAK_API_KEY_PATH).getRealValue();
		ProtectedStringType encryptedApiKey = getMidpointApplication().getProtector().encryptString(apiKey);
		resource.asPrismObject().findOrCreateProperty(passwordPath).setRealValue(encryptedApiKey);

		// assignment
		resource.getParentOrgRef().add(ObjectTypeUtil.createObjectRef(org));

		// hack: adding via model does not work (somehow)
		getMidpointApplication().getRepositoryService().addObject(resource.asPrismObject(), null, task.getResult());
		return resource;
	}

	private void addObject(ObjectType object, Task task) throws CommonException {
		addObject(object, null, task);
	}

	@SuppressWarnings("unchecked")
	private void addObject(ObjectType object, ModelExecuteOptions options, Task task) throws CommonException {
		ObjectDelta<? extends ObjectType> delta = ObjectDelta.createAddDelta(object.asPrismObject());
		Collection<ObjectDelta<? extends ObjectType>> deltas = Collections.<ObjectDelta<? extends ObjectType>>singletonList(delta);
		getModelService().executeChanges(deltas, options, task, task.getResult());
	}

	private boolean isNameUsed(final Class<? extends ObjectType> objectClass, final String name) {
		return runPrivileged(new Producer<Boolean>() {
			@Override
			public Boolean run() {
				Task task = createAnonymousTask(OPERATION_IS_NAME_USED);
				OperationResult result = task.getResult();
				try {
					ObjectQuery query = QueryBuilder.queryFor(objectClass, getPrismContext())
							.item(ObjectType.F_NAME).eq(name).matchingNorm()
							.build();
					return getModelService().countObjects(objectClass, query, null, task, result) > 0;
				} catch (CommonException e) {
					throw new SystemException(e);
				}
			}
		});
	}

}
