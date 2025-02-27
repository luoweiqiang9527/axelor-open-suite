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
package com.axelor.apps.hr.service.expense;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.PrintingTemplate;
import com.axelor.apps.base.db.repo.TraceBackRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.pdf.PdfService;
import com.axelor.apps.base.service.printing.template.PrintingTemplatePrintService;
import com.axelor.apps.base.service.printing.template.model.PrintingGenFactoryContext;
import com.axelor.apps.hr.db.Expense;
import com.axelor.apps.hr.db.ExpenseLine;
import com.axelor.apps.hr.exception.HumanResourceExceptionMessage;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.dms.db.DMSFile;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.utils.helpers.file.PdfHelper;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ExpensePrintServiceImpl implements ExpensePrintService {

  protected static final String DATE_FORMAT_YYYYMMDDHHMM = "YYYYMMddHHmm";

  protected MetaFiles metaFiles;
  protected AppBaseService appBaseService;
  protected HRConfigService hrConfigService;
  protected PrintingTemplatePrintService printingTemplatePrintService;
  protected PdfService pdfService;

  @Inject
  public ExpensePrintServiceImpl(
      MetaFiles metaFiles,
      AppBaseService appBaseService,
      HRConfigService hrConfigService,
      PrintingTemplatePrintService printFromBirtTemplateService,
      PdfService pdfService) {
    this.metaFiles = metaFiles;
    this.appBaseService = appBaseService;
    this.hrConfigService = hrConfigService;
    this.printingTemplatePrintService = printFromBirtTemplateService;
    this.pdfService = pdfService;
  }

  @Override
  public DMSFile uploadExpenseReport(Expense expense) throws IOException, AxelorException {
    String title = getExpenseReportTitle();
    MetaFile metaFile = metaFiles.upload(printAll(expense));
    metaFile.setFileName(title + ".pdf");
    return metaFiles.attach(metaFile, null, expense);
  }

  @Override
  public String getExpenseReportTitle() {
    return I18n.get("Expense")
        + " - "
        + I18n.get("Report")
        + " - "
        + appBaseService
            .getTodayDateTime()
            .format(DateTimeFormatter.ofPattern(DATE_FORMAT_YYYYMMDDHHMM));
  }

  protected File printAll(Expense expense) throws AxelorException, IOException {
    List<File> fileList = new ArrayList<>();
    File reportFile = getReportFile(expense);
    fileList.add(reportFile);
    List<MetaFile> pdfMetaFileList = getExpenseLinePdfJustificationFiles(expense);
    List<MetaFile> imageConvertedMetaFileList =
        pdfService.convertImageToPdf(getExpenseLineImageJustificationFiles(expense));

    fileList.addAll(convertMetaFileToFile(imageConvertedMetaFileList));
    fileList.addAll(convertMetaFileToFile(pdfMetaFileList));

    return PdfHelper.mergePdf(fileList);
  }

  protected File getReportFile(Expense expense) throws AxelorException {
    PrintingTemplate expensePrintTemplate = getExpensePrintingTemplate(expense);
    return printingTemplatePrintService.getPrintFile(
        expensePrintTemplate, new PrintingGenFactoryContext(expense));
  }

  protected PrintingTemplate getExpensePrintingTemplate(Expense expense) throws AxelorException {
    PrintingTemplate expenseReportPrintTemplate =
        hrConfigService.getHRConfig(expense.getCompany()).getExpenseReportPrintTemplate();
    if (expenseReportPrintTemplate == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(HumanResourceExceptionMessage.EXPENSE_BIRT_TEMPLATE_MISSING));
    }

    return expenseReportPrintTemplate;
  }

  protected List<MetaFile> getExpenseLinePdfJustificationFiles(Expense expense) {
    return expense.getGeneralExpenseLineList().stream()
        .map(ExpenseLine::getJustificationMetaFile)
        .filter(Objects::nonNull)
        .filter(file -> "application/pdf".equals(file.getFileType()))
        .collect(Collectors.toList());
  }

  protected List<MetaFile> getExpenseLineImageJustificationFiles(Expense expense) {
    return expense.getGeneralExpenseLineList().stream()
        .map(ExpenseLine::getJustificationMetaFile)
        .filter(Objects::nonNull)
        .filter(file -> file.getFileType().startsWith("image"))
        .collect(Collectors.toList());
  }

  protected List<File> convertMetaFileToFile(List<MetaFile> metaFileList) {
    List<File> fileList = new ArrayList<>();
    for (MetaFile metaFile : metaFileList) {
      Path path = MetaFiles.getPath(metaFile);
      fileList.add(path.toFile());
    }
    return fileList;
  }
}
