/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.budget.db.repo;

import com.axelor.apps.base.db.AdvancedExport;
import com.axelor.apps.base.db.repo.AdvancedExportRepository;
import com.axelor.apps.budget.service.AppBudgetService;
import com.axelor.db.Query;
import com.axelor.inject.Beans;
import java.util.List;

public class AdvancedExportBudgetRepository extends AdvancedExportRepository {

  /** For not allowing user to delete these two records. */
  public static final String EXPORT_ID_1 = "101"; // Budget Template Import Id

  public static final String EXPORT_ID_2 = "102"; // Budget Instance Import Id

  @Override
  public void remove(AdvancedExport entity) {
    if (Beans.get(AppBudgetService.class).isApp("budget")) {
      String importId = entity.getImportId();
      if (importId != null && (importId.equals(EXPORT_ID_1) || importId.equals(EXPORT_ID_2))) {
        return;
      }
    }

    super.remove(entity);
  }

  public List<AdvancedExport> findByMetaModelName(String modelName) {
    return Query.of(AdvancedExport.class)
        .filter("self.metaModel.name = :modelName")
        .bind("modelName", modelName)
        .fetch();
  }
}
