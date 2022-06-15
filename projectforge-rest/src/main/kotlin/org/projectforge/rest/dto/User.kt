/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2022 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.rest.dto

import org.projectforge.business.user.UserDao
import org.projectforge.business.user.UserGroupCache
import org.projectforge.business.user.UserRightValue
import org.projectforge.business.user.service.UserService
import org.projectforge.common.StringHelper
import org.projectforge.framework.access.AccessChecker
import org.projectforge.framework.configuration.ApplicationContextProvider
import org.projectforge.framework.i18n.TimeAgo.getMessage
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.api.UserRightService
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.time.TimeNotation
import java.util.*

class User(
  id: Int? = null,
  displayName: String? = null,
  var username: String? = null,
  var firstname: String? = null,
  var nickname: String? = null,
  var jiraUsername: String? = null,
  var lastname: String? = null,
  var description: String? = null,
  var organization: String? = null,
  var email: String? = null,
  var deactivated: Boolean = false,
  var timeZone: TimeZone? = null,
  var locale: Locale? = null,
  var dateFormat: String? = null,
  var excelDateFormat: String? = null,
  var timeNotation: TimeNotation? = null,
  var personalPhoneIdentifiers: String? = null,
  var assignedGroups: MutableList<Group>? = null,
  var lastLogin: Date? = null,
  var lastLoginTimeAgo: String? = null,
  var sshPublicKey: String? = null,
  var rightsAsString: String? = null,
  var ldapValues: String? = null,
  var localUser: Boolean? = false,
  var lastPasswordChange: Date? = null,
  var lastWlanPasswordChange: Date? = null,
) : BaseDTODisplayObject<PFUserDO>(id = id, displayName = displayName) {

  /**
   * @see copyFromMinimal
   */
  constructor(src: PFUserDO) : this() {
    copyFromMinimal(src)
  }

  override fun copyFromMinimal(src: PFUserDO) {
    super.copyFromMinimal(src)
    this.username = src.username
  }

  override fun copyFrom(src: PFUserDO) {
    super.copyFrom(src)
    lastLoginTimeAgo = getMessage(src.lastLogin)
    timeZone = src.timeZone
    if (accessChecker.isLoggedInUserMemberOfAdminGroup) {
      // Rights
      val sb = StringBuilder()
      userDao.getUserRights(src.id)?.forEachIndexed { index, rightDO ->
        if (index > 0) {
          sb.append(", ")
        }
        sb.append(translate(userRightService.getRightId(rightDO.rightIdString).i18nKey))
        sb.append(
          when (rightDO.value) {
            UserRightValue.READONLY -> " (ro)"
            UserRightValue.PARTLYREADWRITE -> " (prw)"
            UserRightValue.READWRITE -> " (rw)"
            else -> ""
          }
        )
      }
      rightsAsString = sb.toString()
      val newAssignedGroups = mutableSetOf<Group>()
      userGroupCache.getUserGroups(src)?.forEach { groupId ->
        userGroupCache.getGroup(groupId)?.let { groupDO ->
          val group = Group()
          group.copyFromMinimal(groupDO)
          if (!newAssignedGroups.any { it.id == groupDO.id }) {
            newAssignedGroups.add(group)
          }
        }
      }
      assignedGroups = newAssignedGroups.sortedBy { it.displayName }.toMutableList()
    }
  }

  override fun copyTo(dest: PFUserDO) {
    super.copyTo(dest)
    val newAssignedUsers = mutableSetOf<PFUserDO>()
    /*assignedUsers?.forEach { u ->
      UserGroupCache.getInstance().getUser(u.id)?.let { userDO ->
        newAssignedUsers.add(userDO)
      }
    }
    if (newAssignedUsers.isNotEmpty()) {
      dest.assignedUsers = newAssignedUsers
    }*/
  }

  companion object {
    private val accessChecker = ApplicationContextProvider.getApplicationContext().getBean(AccessChecker::class.java)
    private val userDao = ApplicationContextProvider.getApplicationContext().getBean(UserDao::class.java)
    private val userGroupCache = UserGroupCache.getInstance()
    private val userRightService =
      ApplicationContextProvider.getApplicationContext().getBean(UserRightService::class.java)

    fun getUser(userId: Int?, minimal: Boolean = true): User? {
      userId ?: return null
      val userDO = userDao.getOrLoad(userId) ?: return null
      val user = User()
      if (minimal) {
        user.copyFromMinimal(userDO)
      } else {
        user.copyFrom(userDO)
      }
      return user
    }

    /**
     * Converts csv of user ids to list of user (only with id and displayName = "???", no other content).
     */
    fun toUserList(str: String?): List<User>? {
      if (str.isNullOrBlank()) return null
      return toIntArray(str)?.map { User(it, "???") }
    }

    /**
     * Converts csv of user ids to list of user id's.
     */
    fun toIntArray(str: String?): IntArray? {
      if (str.isNullOrBlank()) return null
      return StringHelper.splitToInts(str, ",", false)
    }

    /**
     * Converts user list to ints (of format supported by [toUserList]).
     */
    fun toIntList(users: List<User>?): String? {
      return users?.filter { it.id != null }?.joinToString { "${it.id}" }
    }

    /**
     * Set display names of any existing user in the given list.
     * @see UserService.getUser
     */
    fun restoreDisplayNames(users: List<User>?, userService: UserService) {
      users?.forEach { it.displayName = userService.getUser(it.id)?.displayName }
    }

    /**
     * Converts csv of user ids to list of user.
     */
    fun toUserNames(userIds: String?, userService: UserService): String {
      val users = toUserList(userIds)
      restoreDisplayNames(users, userService)
      return users?.joinToString { it.displayName ?: "???" } ?: ""
    }
  }
}
