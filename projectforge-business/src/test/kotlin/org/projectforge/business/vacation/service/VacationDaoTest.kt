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

package org.projectforge.business.vacation.service

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.projectforge.business.fibu.EmployeeDO
import org.projectforge.business.fibu.EmployeeDao
import org.projectforge.business.fibu.api.EmployeeService
import org.projectforge.business.user.*
import org.projectforge.business.vacation.model.VacationDO
import org.projectforge.business.vacation.model.VacationStatus
import org.projectforge.business.vacation.repository.VacationDao
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.persistence.user.entities.UserRightDO
import org.projectforge.test.AbstractTestBase
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

class VacationDaoTest : AbstractTestBase() {
    @Autowired
    private lateinit var employeeDao: EmployeeDao

    @Autowired
    private lateinit var employeeService: EmployeeService

    @Autowired
    private lateinit var groupDao: GroupDao

    @Autowired
    private lateinit var userDao: UserDao

    @Autowired
    private lateinit var vacationDao: VacationDao

    @Autowired
    private lateinit var vacationService: VacationService

    @Test
    fun vacationAccessTest() {
        val employee = createEmployee("VacationAccessTest.normal", false)
        val manager = createEmployee("VacationAccessTest.manager", false)
        val replacement = createEmployee("VacationAccessTest.replacement", false)
        val vacation = createVacation(employee, manager, replacement, VacationStatus.IN_PROGRESS)
        val foreignVacation = createVacation(replacement, manager, manager, VacationStatus.IN_PROGRESS)
        checkAccess(employee.user, vacation, "own vacation in progress", true, true, true, true, true)
        checkAccess(employee.user, foreignVacation, "foreign vacation", false, false, false, false, false)

        checkAccess(employee.user, vacation, "changed foreign vacation", true, true, false, false, true, foreignVacation)

        vacation.status = VacationStatus.APPROVED
        checkAccess(employee.user, vacation, "own approved vacation", true, false, false, true, true)

        vacation.status = VacationStatus.IN_PROGRESS
        checkAccess(employee.user, vacation, "changed foreign vacation", true, true, false, false, true, foreignVacation)

        val pastVacation = createVacation(employee, manager, replacement, VacationStatus.IN_PROGRESS, future = false)
        checkAccess(employee.user, pastVacation, "own past vacation in progress", true, false, false, true, true)

        pastVacation.status = VacationStatus.APPROVED
        checkAccess(employee.user, pastVacation, "own past approved vacation", true, false, false, false, true)

        // Check self approve
        vacation.manager = employee
        val error = VacationValidator.validate(vacationService, vacation, vacation, false)
        Assertions.assertNotNull(error)
        Assertions.assertEquals(VacationValidator.Error.NOT_ALLOWED_TO_APPROVE.messageKey, error!!.messageKey)
        vacation.manager = manager
        Assertions.assertNull(VacationValidator.validate(vacationService, vacation, vacation, false))

        // Check full access of HR staff:
        val hrEmployee = createEmployee("VacationAccessTest.HR", true)
        checkAccess(hrEmployee.user, vacation, "hr access", true, true, true, true, true)
        checkAccess(hrEmployee.user, foreignVacation, "hr access", true, true, true, true, true)
        checkAccess(hrEmployee.user, pastVacation, "hr access", true, true, true, true, true)

        // Check manager access:
        val approvedVacation = createVacation(employee, manager, replacement, VacationStatus.IN_PROGRESS)
        checkAccess(manager.user, approvedVacation, "manager access", true, false, true, false, true, vacation)

        approvedVacation.startDate = approvedVacation.startDate!!.plusDays(1)
        checkAccess(manager.user, approvedVacation, "manager access", true, false, false, false, true, vacation)

        approvedVacation.startDate = approvedVacation.endDate!!.plusDays(1)
        checkAccess(manager.user, approvedVacation, "manager access", true, false, false, false, true, vacation)

        approvedVacation.startDate = approvedVacation.startDate!!.minusDays(1)
        checkAccess(manager.user, approvedVacation, "manager access", true, false, false, false, true, vacation)

        approvedVacation.startDate = approvedVacation.endDate!!.minusDays(1)
        checkAccess(manager.user, approvedVacation, "manager access", true, false, false, false, true, vacation)

        approvedVacation.special = true
        checkAccess(manager.user, approvedVacation, "manager access not allowed for special approved vacations", true, false, false, false, true, vacation)
    }

    private fun checkAccess(user: PFUserDO?, vacation: VacationDO, msg: String, select: Boolean, insert: Boolean, update: Boolean, delete: Boolean, history: Boolean, dbVacation: VacationDO? = null) {
        if (select) {
            Assertions.assertTrue(vacationDao.hasUserSelectAccess(user, vacation, false), "Select access allowed: $msg.")
        } else {
            Assertions.assertFalse(vacationDao.hasUserSelectAccess(user, vacation, false), "Select access not allowed: $msg.")
            try {
                vacationDao.hasHistoryAccess(user, vacation, true)
                fail("Exception expected, select access not allowed: $msg.")
            } catch (ex: Exception) {
                // OK
            }
        }
        if (insert) {
            Assertions.assertTrue(vacationDao.hasInsertAccess(user, vacation, false), "Insert access allowed: $msg.")
        } else {
            Assertions.assertFalse(vacationDao.hasInsertAccess(user, vacation, false), "Insert access not allowed: $msg.")
            try {
                vacationDao.hasInsertAccess(user, vacation, true)
                fail("Exception expected, insert access not allowed: $msg.")
            } catch (ex: Exception) {
                // OK
            }
        }
        if (update) {
            Assertions.assertTrue(vacationDao.hasUpdateAccess(user, vacation, dbVacation, false), "Update access allowed: $msg.")
        } else {
            Assertions.assertFalse(vacationDao.hasUpdateAccess(user, vacation, dbVacation, false), "Update access not allowed: $msg.")
            try {
                vacationDao.hasUpdateAccess(user, vacation, dbVacation, true)
                fail("Exception expected, update access not allowed: $msg.")
            } catch (ex: Exception) {
                // OK
            }
        }
        if (delete) {
            Assertions.assertTrue(vacationDao.hasDeleteAccess(user, vacation, dbVacation, false), "Delete access allowed: $msg.")
        } else {
            Assertions.assertFalse(vacationDao.hasDeleteAccess(user, vacation, dbVacation, false), "Delete access not allowed: $msg.")
            try {
                vacationDao.hasDeleteAccess(user, vacation, dbVacation, true)
                fail("Exception expected, delete access not allowed: $msg.")
            } catch (ex: Exception) {
                // OK
            }
        }
        if (history) {
            Assertions.assertTrue(vacationDao.hasHistoryAccess(user, vacation, false), "History access allowed: $msg.")
        } else {
            Assertions.assertFalse(vacationDao.hasHistoryAccess(user, vacation, false), "History access not allowed: $msg.")
            try {
                vacationDao.hasHistoryAccess(user, vacation, true)
                fail("Exception expected, history access not allowed: $msg.")
            } catch (ex: Exception) {
                // OK
            }
        }
    }

    private fun createVacation(employee: EmployeeDO, manager: EmployeeDO, replacement: EmployeeDO, status: VacationStatus, future: Boolean = true): VacationDO {
        var vacation = VacationDO()
        vacation.employee = employee
        vacation.manager = manager
        vacation.replacement = replacement
        if (future) {
            vacation.startDate = LocalDate.now().plusDays(2)
            vacation.endDate = LocalDate.now().plusDays(10)
        } else {
            vacation.startDate = LocalDate.now().minusDays(10)
            vacation.endDate = LocalDate.now().minusDays(2)
        }
        vacation.status = status
        return vacation
    }

    private fun createEmployee(name: String, hrAccess: Boolean): EmployeeDO {
        val loggedInUser = ThreadLocalUserContext.getUser()
        logon(TEST_ADMIN_USER)
        val user = PFUserDO()
        user.firstname = name
        user.lastname = name
        user.username = "$name.$name"
        if (hrAccess) {
            user.addRight(UserRightDO(UserRightId.HR_VACATION, UserRightValue.READWRITE))
        }
        initTestDB.addUser(user);
        if (hrAccess) {
            val group = getGroup(ProjectForgeGroup.HR_GROUP.toString())
            group.assignedUsers!!.add(user)
            groupDao.update(group)
        }
        val employee = EmployeeDO()
        employee.user = user
        employeeService.addNewAnnualLeaveDays(employee, LocalDate.now().minusYears(2), BigDecimal(30));
        employeeDao.internalSave(employee)
        logon(loggedInUser)
        return employee
    }
}
