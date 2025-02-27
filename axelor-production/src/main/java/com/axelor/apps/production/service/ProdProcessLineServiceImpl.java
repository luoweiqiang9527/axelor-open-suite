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
package com.axelor.apps.production.service;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.production.db.Machine;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.db.ProdProcess;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.WorkCenter;
import com.axelor.apps.production.db.repo.WorkCenterRepository;
import com.axelor.apps.production.exceptions.ProductionExceptionMessage;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

public class ProdProcessLineServiceImpl implements ProdProcessLineService {

  protected WorkCenterService workCenterService;

  @Inject
  public ProdProcessLineServiceImpl(WorkCenterService workCenterService) {
    this.workCenterService = workCenterService;
  }

  @Override
  public long computeEntireCycleDuration(
      OperationOrder operationOrder, ProdProcessLine prodProcessLine, BigDecimal qty)
      throws AxelorException {
    WorkCenter workCenter = prodProcessLine.getWorkCenter();

    Pair<Long, BigDecimal> durationNbCyclesPair =
        getDurationNbCyclesPair(workCenter, prodProcessLine, qty);
    long duration = durationNbCyclesPair.getLeft().longValue();
    BigDecimal nbCycles = durationNbCyclesPair.getRight();

    BigDecimal machineDurationPerCycle =
        new BigDecimal(Optional.ofNullable(prodProcessLine.getDurationPerCycle()).orElse(0l));
    BigDecimal humanDurationPerCycle =
        new BigDecimal(Optional.ofNullable(prodProcessLine.getHumanDuration()).orElse(0l));
    BigDecimal maxDurationPerCycle =
        getMaxDuration(Arrays.asList(machineDurationPerCycle, humanDurationPerCycle));

    long plannedDuration = 0;
    long machineDuration = duration + nbCycles.multiply(machineDurationPerCycle).longValue();
    long humanDuration = nbCycles.multiply(humanDurationPerCycle).longValue();

    if (machineDurationPerCycle.equals(maxDurationPerCycle)) {
      plannedDuration = machineDuration;
    } else if (humanDurationPerCycle.equals(maxDurationPerCycle)) {
      plannedDuration = humanDuration;
    }

    if (operationOrder != null) {
      operationOrder.setPlannedMachineDuration(machineDuration);
      operationOrder.setPlannedHumanDuration(humanDuration);
    }

    return plannedDuration;
  }

  @Override
  public long computeEntireDuration(ProdProcess prodProcess, BigDecimal qty)
      throws AxelorException {
    long totalDuration = 0;
    for (ProdProcessLine prodProcessLine : prodProcess.getProdProcessLineList()) {
      totalDuration += this.computeEntireCycleDuration(null, prodProcessLine, qty);
    }
    return totalDuration;
  }

  @Override
  public long computeLeadTimeDuration(ProdProcess prodProcess, BigDecimal qty)
      throws AxelorException {

    Map<Integer, Long> maxDurationPerPriority = new HashMap<>();
    for (ProdProcessLine prodProcessLine : prodProcess.getProdProcessLineList()) {
      Integer priority = prodProcessLine.getPriority();
      Long duration = maxDurationPerPriority.get(priority);
      Long computedDuration = this.computeEntireCycleDuration(null, prodProcessLine, qty);

      if (duration == null || computedDuration > duration) {
        maxDurationPerPriority.put(priority, computedDuration);
      }
    }

    return maxDurationPerPriority.values().stream().mapToLong(l -> l).sum();
  }

  @Override
  public Integer getNextPriority(ProdProcess prodProcess, Integer priority) {
    if (priority == null
        || prodProcess == null
        || CollectionUtils.isEmpty(prodProcess.getProdProcessLineList())) {
      return null;
    }
    return prodProcess.getProdProcessLineList().stream()
        .filter(ppl -> ppl.getPriority() > priority)
        .min(Comparator.comparingInt(ProdProcessLine::getPriority))
        .map(ProdProcessLine::getPriority)
        .orElse(null);
  }

  protected Pair<Long, BigDecimal> getDurationNbCyclesPair(
      WorkCenter workCenter, ProdProcessLine prodProcessLine, BigDecimal qty)
      throws AxelorException {
    long duration = 0;
    if (prodProcessLine.getWorkCenter() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(ProductionExceptionMessage.PROD_PROCESS_LINE_MISSING_WORK_CENTER),
          prodProcessLine.getProdProcess() != null
              ? prodProcessLine.getProdProcess().getCode()
              : "null",
          prodProcessLine.getName());
    }

    BigDecimal maxCapacityPerCycle = prodProcessLine.getMaxCapacityPerCycle();

    BigDecimal nbCycles;
    if (maxCapacityPerCycle.compareTo(BigDecimal.ZERO) == 0) {
      nbCycles = qty;
    } else {
      nbCycles = qty.divide(maxCapacityPerCycle, 0, RoundingMode.UP);
    }

    int workCenterTypeSelect = workCenter.getWorkCenterTypeSelect();

    if (workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_MACHINE
        || workCenterTypeSelect == WorkCenterRepository.WORK_CENTER_TYPE_BOTH) {
      Machine machine = workCenter.getMachine();
      if (machine == null) {
        throw new AxelorException(
            workCenter,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(ProductionExceptionMessage.WORKCENTER_NO_MACHINE),
            workCenter.getName());
      }
      duration += prodProcessLine.getStartingDuration();
      duration += prodProcessLine.getEndingDuration();
      duration +=
          nbCycles
              .subtract(new BigDecimal(1))
              .multiply(new BigDecimal(prodProcessLine.getSetupDuration()))
              .longValue();
    }

    return Pair.of(Long.valueOf(duration), nbCycles);
  }

  protected BigDecimal getMaxDuration(List<BigDecimal> durations) {
    return !CollectionUtils.isEmpty(durations) ? Collections.max(durations) : BigDecimal.ZERO;
  }
}
