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

package org.projectforge.rest

import org.projectforge.common.anots.PropertyInfo

class My2FAData {
  @PropertyInfo(i18nKey = "user.My2FACode.code", tooltip = "user.My2FACode.code.info")
  var code: String? = null

  /**
   * Login password is only needed as additional security factor if OTP is sent via e-mail.
   */
  var password: CharArray? = null

  /**
   * Info string: last successful 2FA as human readable String (TimeAgo): "3 minutes ago"
   */
  var lastSuccessful2FA: String? = null

  /**
   * Only used for setup.
   */
  var mobilePhone: String? = null

  /**
   * Only used for setup (must be hidden for other pages)
   */
  var authenticatorKey: String? = null

  /**
   * Only used for setup.
   */
  var showAuthenticatorKey: Boolean = false
}
