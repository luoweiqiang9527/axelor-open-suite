/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2016 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.studio.service.builder;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaSelect;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.loader.XMLViews;
import com.axelor.meta.schema.ObjectViews;
import com.axelor.meta.schema.actions.ActionRecord;
import com.axelor.meta.schema.actions.ActionRecord.RecordField;
import com.axelor.meta.schema.views.AbstractView;
import com.axelor.studio.db.Filter;
import com.axelor.studio.db.ViewBuilder;
import com.axelor.studio.service.FilterService;
import com.axelor.studio.service.ViewLoaderService;
import com.google.inject.Inject;

/**
 * This class generate charts using ViewBuilder and chart related fields. Chart
 * xml generated by adding query, search fields , onInit actions..etc. All
 * filters with parameter checked will be used as search fields. Tags also there
 * to use context variable in filter value, like $User for current user
 * (__user__).
 */

public class ChartBuilderService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private static final String Tab1 = "\n \t";
	private static final String Tab2 = "\n \t\t";
	private static final String Tab3 = "\n \t\t\t";

	private List<String> searchFields;

	private ActionRecord onNewAction;

	private List<RecordField> onNewFields;

	@Inject
	private ViewLoaderService viewLoaderService;

	@Inject
	private FilterService filterService;

	@Inject
	private MetaModelRepository metaModelRepo;

	/**
	 * Root Method to access the service it generate AbstractView from
	 * ViewBuilder.
	 * 
	 * @param viewBuilder
	 *            ViewBuilder object of type chart.
	 * @return AbstractView from meta schema.
	 * @throws JAXBException
	 */
	public AbstractView getView(ViewBuilder viewBuilder) throws JAXBException {

		searchFields = new ArrayList<String>();
		onNewFields = new ArrayList<RecordField>();
		onNewAction = null;

		String[] queryString = prepareQuery(viewBuilder);
		setOnNewAction(viewBuilder);

		String xml = "<chart name=\"" + viewBuilder.getName() + "\" title=\""
				+ viewBuilder.getTitle() + "\" ";

		if (onNewAction != null) {
			xml += " onInit=\"" + onNewAction.getName() + "\" ";
		}
		xml += ">\n";
		if (!searchFields.isEmpty()) {
			xml += "\t" + getSearchFields() + "\n";
		}

		xml += "\t<dataset type=\"jpql\">";
		xml += Tab2 + queryString[0];
		xml += Tab2 + "</dataset>";
		xml += Tab1 + "<category key=\"groupField\" type=\"text\" title=\""
				+ viewBuilder.getGroupOn().getLabel() + "\" />";
		xml += Tab1 + "<series key=\"fieldSum\" type=\""
				+ viewBuilder.getChartType() + "\" title=\""
				+ viewBuilder.getDisplayField().getLabel() + "\" ";
		if (queryString[1] != null) {
			xml += "groupBy=\"aggField\" ";
		}
		xml += "/>\n";
		xml += "</chart>";

		log.debug("Chart xml: {}", xml);

		ObjectViews chartView = XMLViews.fromXML(xml);

		return chartView.getViews().get(0);
	}

	/**
	 * Method to get generated on onNew ActionRecord during view generation.
	 * 
	 * @return ActionRecord class of meta schema.
	 */
	public ActionRecord getOnNewAction() {

		log.debug("On new chart: {}", onNewAction);

		return onNewAction;
	}

	/**
	 * Method create query from chart filters added in chart builder.
	 * 
	 * @param viewBuilder
	 *            ViewBuilder of type chart
	 * @return StringArray with first element as query string and second as
	 *         aggregate field name.
	 */
	private String[] prepareQuery(ViewBuilder viewBuilder) {

		String query = "SELECT" + Tab3 + "SUM(self."
				+ viewBuilder.getDisplayField().getName() + ") AS fieldSum,"
				+ Tab3;

		String groupField = getGroupFieldName(viewBuilder.getGroupOn(),
				viewBuilder.getGroupDateType(), viewBuilder.getGroupOnTarget());

		String aggField = getGroupFieldName(viewBuilder.getAggregateOn(),
				viewBuilder.getAggregateDateType(),
				viewBuilder.getAggregateOnTarget());

		query += groupField + " AS groupField";

		if (aggField != null) {
			query += "," + Tab3 + aggField + " AS aggField";
		}

		query += Tab2 + "FROM " + Tab3 + viewBuilder.getMetaModel().getName()
				+ " self";

		String filters = createFilters(viewBuilder.getFilterList());

		if (filters != null) {
			query += Tab2 + "WHERE " + Tab3 + filters;
		}

		query += Tab2 + "GROUP BY " + Tab3 + groupField;

		if (aggField != null) {
			query += "," + aggField;
		}

		return new String[] { query, aggField };
	}

	/**
	 * Method to get correct format of group field use to add in query
	 * 
	 * @param metaField
	 *            Group field name selected in chart builder.
	 * @param dateType
	 *            Type of date (month,year,day) selected for group field.
	 * @return Group field string.
	 */
	private String getGroupFieldName(MetaField metaField, String dateType,
			String target) {

		if (metaField == null) {
			return null;
		}

		String name = metaField.getName();
		String typeName = metaField.getTypeName();

		if (dateType != null
				&& "LocalDate,DateTime,LocalDateTime".contains(typeName)) {

			switch (dateType) {
			case "year":
				return "YEAR(self." + name + ")";
			case "month":
				return "concat(str(YEAR(self." + name
						+ ")),'-',str(MONTH(self." + name + ")))";
			default:
				return "self." + name;
			}

		} else {
			if (target == null) {
				target = name + ".name";
			}
			return "self." + target;

		}

	}

	/**
	 * Method to get name column for relational field, as it is required to
	 * compare in chart filters.
	 * 
	 * @param metaField
	 *            Relational meta field.
	 * @return String array with first element as nameColumn and second as type
	 *         of nameColumn.
	 */
	// private String[] getNameColumn(MetaField metaField){
	//
	// String nameColumn = "name";
	// String typeName = "string";
	// String modelName = metaField.getTypeName();
	// MetaModel metaModel = metaModelRepo.findByName(modelName);
	//
	// try {
	// Mapper mapper = Mapper.of(Class.forName(metaModel.getFullName()));
	// for(Property property : Arrays.asList(mapper.getProperties())){
	// if(property.isNameColumn()){
	// nameColumn = property.getName();
	// typeName = property.getType().name();
	// break;
	// }
	// }
	// } catch (ClassNotFoundException e) {
	// }
	//
	// return new
	// String[]{metaModel.getFullName(),nameColumn,typeName.toUpperCase()};
	//
	// }

	/**
	 * Method generate xml for search-fields.
	 * 
	 * @return
	 */
	private String getSearchFields() {

		String search = "<search-fields>";

		for (String searchField : searchFields) {
			search += Tab2 + searchField;
		}
		search += Tab1 + "</search-fields>";

		return search;
	}

	/**
	 * Method set default value for search-fields(parameters). It will add field
	 * and expression in onNew for chart.
	 * 
	 * @param fieldName
	 *            Name of field of search-field.
	 * @param typeName
	 *            Type of field.
	 * @param defaultValue
	 *            Default value input in chart filter.
	 * @param modelField
	 *            It is for relational field. String array with first element as
	 *            Model name and second as its field.
	 */
	private void setDefaultValue(String fieldName, String typeName,
			String defaultValue, String[] modelField) {

		if (defaultValue == null) {
			return;
		}

		RecordField field = new RecordField();
		field.setName(fieldName);

		defaultValue = filterService.getTagValue(defaultValue, false);

		if (modelField != null) {
			if (typeName.equals("STRING")) {
				defaultValue = "__repo__.of(" + modelField[0]
						+ ").all().filter(\"LOWER(" + modelField[1] + ") LIKE "
						+ defaultValue + "\").fetchOne()";
			} else {
				defaultValue = "__repo__.of(" + modelField[0]
						+ ").all().filter(\"" + modelField[1] + " = "
						+ defaultValue + "\").fetchOne()";
			}

		}

		log.debug("Default value: {}", defaultValue);

		field.setExpression("eval:" + defaultValue);

		onNewFields.add(field);
	}

	/**
	 * It will create onNew action from onNew fields.
	 * 
	 * @param viewBuilder
	 *            ViewBuilder use to get model name also used in onNew action
	 *            name creation.
	 */
	private void setOnNewAction(ViewBuilder viewBuilder) {

		if (!onNewFields.isEmpty()) {
			onNewAction = new ActionRecord();
			onNewAction.setName("action-" + viewBuilder.getName() + "-default");
			onNewAction.setModel(viewBuilder.getModel());
			onNewAction.setFields(onNewFields);
		}

	}

	private String createFilters(List<Filter> filterList) {

		String filters = null;

		for (Filter filter : filterList) {

			MetaField field = filter.getMetaField();

			String relationship = field.getRelationship();
			String condition = "";

			if (relationship != null) {
				condition = filterService.getRelationalCondition(filter, null);
			} else {
				condition = filterService.getSimpleCondition(filter, null);
			}

			if (filter.getIsParameter()) {
				addSearchField(field, filter.getTargetField(),
						filter.getDefaultValue());
			}

			if (filters == null) {
				filters = condition;
			} else {
				String opt = filter.getLogicOp() == 0 ? " AND " : " OR ";
				filters = filters + opt + condition;
			}
		}

		return filters;

	}

	private void addSearchField(MetaField field, String targetField,
			String defaultVal) {

		field = (MetaField) filterService.getTargetField(field, targetField)
				.get(1);
		String relationship = field.getRelationship();
		String fieldName = field.getName();
		String typeName = field.getTypeName();
		String fieldStr = "<field name=\"" + field.getName() + "\" title=\""
				+ field.getLabel();
		String[] modelField = null;

		if (relationship == null) {
			String fieldType = viewLoaderService.getFieldType(field);
			fieldStr += "\" type=\"" + fieldType;
			MetaSelect select = field.getMetaSelect();
			if (select != null) {
				fieldStr = fieldStr + "\" selection=\"" + select.getName();
			}
		} else {
			String targetRef = metaModelRepo.findByName(typeName).getFullName();
			fieldStr += "\" type=\"reference\" target=\"" + targetRef;
			modelField = new String[] { typeName, "self." + fieldName };
		}

		searchFields.add(fieldStr + "\" x-required=\"true\" />");

		setDefaultValue(fieldName, typeName, defaultVal, modelField);

	}

}
