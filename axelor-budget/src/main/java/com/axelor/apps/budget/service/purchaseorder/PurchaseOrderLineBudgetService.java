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
package com.axelor.apps.budget.service.purchaseorder;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;

public interface PurchaseOrderLineBudgetService {

  /**
   * Clear budget distribution, compute the budget key related to this configuration of account and
   * analytic, find the budget related to this key and the purchase order date. Then create an
   * automatic budget distribution with the company ex tax total and save the purchase order line.
   * Return an error message if a budget distribution is not generated
   *
   * @param purchaseOrder
   * @param purchaseOrderLine
   * @return String
   */
  public String computeBudgetDistribution(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine) throws AxelorException;

  /**
   * If multi budget, compute budget distribution line's budget name to fill budget name string
   * field Else, fill budget name string with the budget's name
   *
   * @param purchaseOrderLine, multiBudget
   */
  public void fillBudgetStrOnLine(PurchaseOrderLine purchaseOrderLine, boolean multiBudget);

  /**
   * If multi budget, compute budget distribution line's budget name to fill budget name string
   * field Else, fill budget name string with the budget's name
   *
   * @param purchaseOrderLine, multiBudget
   * @return String
   */
  public String searchAndFillBudgetStr(PurchaseOrderLine purchaseOrderLine, boolean multiBudget);

  /**
   * Get domain for budget field via purchase order line using group, section, line from it and
   * order date from purchase order
   *
   * @param purchaseOrderLine, purchaseOrder
   * @return String
   */
  public String getBudgetDomain(PurchaseOrderLine purchaseOrderLine, PurchaseOrder purchaseOrder)
      throws AxelorException;

  /**
   * Take all budget distribution on this purchase order line and throw an error if the total amount
   * of budget distribution is superior to company ex tax total of the purchase order line
   *
   * @param purchaseOrderLine
   * @throws AxelorException
   */
  public void checkAmountForPurchaseOrderLine(PurchaseOrderLine purchaseOrderLine)
      throws AxelorException;
}
