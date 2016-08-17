/*
 * Copyright (c) 2010-2016 Evolveum
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
package com.evolveum.smartwidgets.gui.forms;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.repo.api.query.Query;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.assignment.AssignmentEditorDto;
import com.evolveum.midpoint.web.component.form.Form;
import com.evolveum.midpoint.web.component.objectdetails.AbstractFocusTabPanel;
import com.evolveum.midpoint.web.component.prism.ObjectWrapper;
import com.evolveum.midpoint.web.page.admin.users.dto.FocusSubwrapperDto;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ServiceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.smartwidgets.Constants;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.RepeatingView;

import java.util.List;


/**
 * @author mederly
 */
public class MeasurementsTabPanel extends AbstractFocusTabPanel<OrgType> {
	private static final long serialVersionUID = 1L;

	private static final String DOT_CLASS = MeasurementsTabPanel.class.getName() + ".";
	private static final String OPERATION_GET_DEVICES = DOT_CLASS + "getDevices";

	private static final Trace LOGGER = TraceManager.getTrace(MeasurementsTabPanel.class);
	private static final String ID_CHARTS = "charts";

	public MeasurementsTabPanel(String id, Form mainForm,
			LoadableModel<ObjectWrapper<OrgType>> focusWrapperModel,
			LoadableModel<List<AssignmentEditorDto>> assignmentsModel, 
			LoadableModel<List<FocusSubwrapperDto<ShadowType>>> projectionModel,
			PageBase pageBase) {
		super(id, mainForm, focusWrapperModel, assignmentsModel, projectionModel, pageBase);
		initLayout(focusWrapperModel, pageBase);
	}

	private void initLayout(final LoadableModel<ObjectWrapper<OrgType>> orgModel, PageBase pageBase) {

		RepeatingView charts = new RepeatingView(ID_CHARTS);

		try {
			Task task = pageBase.createSimpleTask(OPERATION_GET_DEVICES);
			ObjectQuery q = QueryBuilder.queryFor(ServiceType.class, pageBase.getPrismContext())
					.isChildOf(orgModel.getObject().getOid()).build();
			SearchResultList<PrismObject<ServiceType>> devices = pageBase.getModelService()
					.searchObjects(ServiceType.class, q, null, task, task.getResult());
			if (!devices.isEmpty()) {
				for (PrismObject<ServiceType> device : devices) {
					String channel = device.asObjectable().getIdentifier();
					PrismProperty<String> readKeyProp = device.findProperty(Constants.CHANNEL_READ_KEY_PATH);
					String readKey = readKeyProp != null ? readKeyProp.getRealValue() : null;
					if (channel != null && readKey != null) {
						Label label = new Label(charts.newChildId(), "<iframe width=\"450\" height=\"260\" style=\"border: 1px solid #cccccc;\"\n"
								+ "src=\"https://thingspeak.com/channels/" + channel + "/charts/1?api_key=" + readKey
								+ "&bgcolor=%23ffffff&color=%23d62020&dynamic=true&results=60&type=line\">\n"
								+ "</iframe>\n");
						label.setEscapeModelStrings(false);
						charts.add(label);
					}
				}
			} else {
				charts.add(new Label(charts.newChildId(), "There are no devices in this organization unit."));
			}
		} catch (RuntimeException | CommonException e) {
			charts.add(new Label(charts.newChildId(), "Couldn't get devices: " + e.getMessage()));
		}
		add(charts);
	}

}
