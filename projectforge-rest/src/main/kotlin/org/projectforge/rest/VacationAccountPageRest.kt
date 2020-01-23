/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.rest

import org.projectforge.business.fibu.api.EmployeeService
import org.projectforge.business.user.service.UserPrefService
import org.projectforge.business.vacation.model.VacationDO
import org.projectforge.business.vacation.service.VacationService
import org.projectforge.common.DateFormatType
import org.projectforge.framework.i18n.translateMsg
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.time.PFDateTimeUtils
import org.projectforge.rest.config.Rest
import org.projectforge.rest.dto.Employee
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Year

@RestController
@RequestMapping("${Rest.URL}/vacationAccount")
class VacationAccountPageRest {
    class Data(var employee: Employee? = null)

    @Autowired
    private lateinit var employeeService: EmployeeService

    @Autowired
    private lateinit var userPrefService: UserPrefService

    @Autowired
    private lateinit var vacationService: VacationService

    @GetMapping("layout")
    fun getLayout(): UILayout {
        val layout = UILayout("vacation.leaveaccount.title")
        val lc = LayoutContext(VacationDO::class.java)
        layout.addTranslations("employee",
                "vacation.leaveaccount.title",
                "vacation.annualleave",
                "vacation.previousyearleave",
                "vacation.subtotal",
                "menu.vacation.leaveAccountEntry")
        val endOfYear = vacationService.getEndOfCarryVacationOfPreviousYear(Year.now().value)
        val endOfYearString = PFDateTimeUtils.ensureUsersDateTimeFormat(DateFormatType.DATE_WITHOUT_YEAR).format(endOfYear)
        layout.addTranslation("vacation.previousyearleaveunused", translateMsg("vacation.previousyearleaveunused", endOfYearString))

        val userPref = getUserPref()
        var employeeId = userPref.employeeId ?: ThreadLocalUserContext.getUserContext().employeeId
        val employee = employeeService.getById(employeeId)
        val statistics = mutableMapOf<String, Any>()
        if (employee != null) {
            statistics["statisticsCurrentYear"] = vacationService.getVacationStats(employee, Year.now().value)
            statistics["statisticsPreviousYear"] = vacationService.getVacationStats(employee, Year.now().value - 1)
        }
        layout.add(UIFieldset(length = 12)
                .add(UIRow()
                        .add(UICol(length = 6)
                                .add(lc, "employee")))
                .add(UIRow()
                        .add(UICol(length = 6)
                                .add(UICustomized("vacation.statistics",
                                        values = statistics)))))
                .add(UIRow()
                        .add(UICol(length = 6)
                                .add(UIButton("add", "add", UIColor.SUCCESS, responseAction = ResponseAction("http://localhost:3000/react/vacation/edit")))))
        return layout
    }

    class VacationAccountUserPref(var employeeId: Int? = null)

    private fun getUserPref(): VacationAccountUserPref {
        return userPrefService.ensureEntry("vacation", "account", VacationAccountUserPref())
    }
}
