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
package com.axelor.apps.production.service.operationorder.planning;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.WeeklyPlanning;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.production.db.Machine;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.db.repo.OperationOrderRepository;
import com.axelor.apps.production.model.machine.MachineTimeSlot;
import com.axelor.apps.production.service.machine.MachineService;
import com.axelor.apps.production.service.operationorder.OperationOrderOutsourceService;
import com.axelor.apps.production.service.operationorder.OperationOrderService;
import com.axelor.apps.production.service.operationorder.OperationOrderStockMoveService;
import com.axelor.utils.helpers.date.DurationHelper;
import com.axelor.utils.helpers.date.LocalDateTimeHelper;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;

public class OperationOrderPlanningAtTheLatestInfiniteCapacityService
    extends OperationOrderPlanningCommonService {

  protected OperationOrderPlanningInfiniteCapacityService
      operationOrderPlanningInfiniteCapacityService;
  protected WeeklyPlanningService weeklyPlanningService;
  protected MachineService machineService;

  @Inject
  protected OperationOrderPlanningAtTheLatestInfiniteCapacityService(
      OperationOrderService operationOrderService,
      OperationOrderStockMoveService operationOrderStockMoveService,
      OperationOrderRepository operationOrderRepository,
      AppBaseService appBaseService,
      OperationOrderOutsourceService operationOrderOutsourceService,
      OperationOrderPlanningInfiniteCapacityService operationOrderPlanningInfiniteCapacityService,
      WeeklyPlanningService weeklyPlanningService,
      MachineService machineService) {
    super(
        operationOrderService,
        operationOrderStockMoveService,
        operationOrderRepository,
        appBaseService,
        operationOrderOutsourceService);
    this.operationOrderPlanningInfiniteCapacityService =
        operationOrderPlanningInfiniteCapacityService;
    this.weeklyPlanningService = weeklyPlanningService;
    this.machineService = machineService;
  }

  @Override
  protected void planWithStrategy(OperationOrder operationOrder) throws AxelorException {
    if (operationOrder.getOutsourcing()) {
      planWithStrategyAtTheLatestOutSourced(operationOrder);
    } else {
      planWithStrategyNotOutSourced(operationOrder);
    }
  }

  protected void planWithStrategyNotOutSourced(OperationOrder operationOrder)
      throws AxelorException {

    Machine machine = operationOrder.getMachine();
    WeeklyPlanning weeklyPlanning = null;
    LocalDateTime plannedEndDate = operationOrder.getPlannedEndDateT();

    LocalDateTime nextOperationDate = operationOrderService.getNextOperationDate(operationOrder);
    LocalDateTime minDate = LocalDateTimeHelper.min(plannedEndDate, nextOperationDate);
    operationOrder.setPlannedEndDateT(minDate);

    if (machine != null) {
      MachineTimeSlot freeMachineTimeSlot =
          machineService.getFurthestTimeSlotFrom(
              machine,
              minDate.minusSeconds(operationOrderService.getDuration(operationOrder)),
              minDate,
              operationOrder);
      operationOrder.setPlannedStartDateT(freeMachineTimeSlot.getStartDateT());
      operationOrder.setPlannedEndDateT(freeMachineTimeSlot.getEndDateT());

      Long plannedDuration =
          DurationHelper.getSecondsDuration(
              Duration.between(
                  operationOrder.getPlannedStartDateT(), operationOrder.getPlannedEndDateT()));
      operationOrder.setPlannedDuration(plannedDuration);
    } else {
      operationOrder.setPlannedEndDateT(minDate);
      operationOrder.setPlannedStartDateT(
          operationOrder
              .getPlannedEndDateT()
              .minusSeconds(operationOrderService.getDuration(operationOrder)));

      Long plannedDuration =
          DurationHelper.getSecondsDuration(
              Duration.between(
                  operationOrder.getPlannedStartDateT(), operationOrder.getPlannedEndDateT()));
      operationOrder.setPlannedDuration(plannedDuration);
    }

    checkIfPlannedStartDateTimeIsBeforeCurrentDateTime(operationOrder);
  }

  public LocalDateTime computePlannedStartDateT(OperationOrder operationOrder)
      throws AxelorException {

    if (operationOrder.getWorkCenter() != null) {
      return operationOrder
          .getPlannedEndDateT()
          .minusSeconds(
              (int)
                  operationOrderService.computeEntireCycleDuration(
                      operationOrder, operationOrder.getManufOrder().getQty()));
    }

    return operationOrder.getPlannedEndDateT();
  }
}
