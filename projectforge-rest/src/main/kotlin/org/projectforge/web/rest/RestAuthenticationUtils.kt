/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2021 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.web.rest

import mu.KotlinLogging
import org.projectforge.SystemStatus
import org.projectforge.business.login.LoginProtection
import org.projectforge.business.user.UserAccessLogEntries
import org.projectforge.business.user.UserAuthenticationsService
import org.projectforge.business.user.UserTokenType
import org.projectforge.business.user.filter.UserFilter
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.api.UserContext
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.rest.Authentication
import org.projectforge.rest.AuthenticationOld
import org.projectforge.rest.ConnectionSettings
import org.projectforge.rest.converter.DateTimeFormat
import org.projectforge.rest.utils.RequestLog
import org.projectforge.security.SecurityLogging
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.io.IOException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private val log = KotlinLogging.logger {}

/**
 * Does the authentication stuff for restful requests.
 *
 * @author Daniel Ludwig (d.ludwig@micromata.de)
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
@Service
open class RestAuthenticationUtils {
  @Autowired
  private lateinit var userAuthenticationsService: UserAuthenticationsService

  @Autowired
  private lateinit var systemStatus: SystemStatus

  /**
   * Checks also login protection (time out against brute force attack).
   * @param userTokenType Type of authentication (or null) for separating time penalties of login protection for different types (DAV, REST_CLIENT and normal login).
   */
  fun getUserString(
    authInfo: RestAuthenticationInfo,
    userAttributes: Array<String>,
    userTokenType: UserTokenType?,
    required: Boolean
  ): String? {
    authInfo.userString = getAttribute(authInfo.request, *userAttributes)
    if (authInfo.userString.isNullOrBlank()) {
      if (required) {
        logError(
          authInfo,
          "Authentication failed, no user given by request params ${joinToString(userAttributes)}. Rest call forbidden."
        )
      } else if (log.isDebugEnabled) {
        logDebug(authInfo, "Can't get user String by request parameters ${joinToString(userAttributes)} (OK).")
      }
      return null
    }
    if (checkLoginProtection(authInfo, userTokenType)) {
      // Access denied (time offset due to failed logins). Logging is done by check method.
      return null
    }
    if (log.isDebugEnabled) {
      logDebug(authInfo, "Got user by request parameters ${joinToString(userAttributes)}: ${authInfo.userString}.")
    }
    return authInfo.userString
  }

  fun getUserSecret(authInfo: RestAuthenticationInfo, secretAttributes: Array<String>): String? {
    val secret = getAttribute(authInfo.request, *secretAttributes)
    if (secret.isNullOrBlank()) {
      // Log message, because userString was found, but authentication token not:
      logError(
        authInfo,
        "Authentication failed, no user secret (authentication token) given by request params ${
          joinToString(secretAttributes)
        }. Rest call forbidden."
      )
      return null
    } else if (log.isDebugEnabled) {
      logDebug(authInfo, "Got user secret by request parameters ${joinToString(secretAttributes)}.")
    }
    return secret
  }

  /**
   * Tries a basic authorization by getting the "Authorization" header containing String "Basic" and base64 encoded "user:secret".
   * @param userTokenType Type of authentication (or null) for separating time penalties of login protection for different types (DAV, REST_CLIENT and normal login).
   * @param required If required, an error message is logged if no authentication is present. Otherwise only wrong credentials will result in error messages.
   */
  fun basicAuthentication(
    authInfo: RestAuthenticationInfo,
    userTokenType: UserTokenType,
    required: Boolean,
    authenticate: (userString: String, secret: String) -> PFUserDO?
  ) {
    if (log.isDebugEnabled) {
      logDebug(authInfo, "Trying basic authentication...")
    }
    val authHeader = getHeader(authInfo.request, "authorization", "Authorization")
    if (authHeader.isNullOrBlank()) {
      if (required) {
        authInfo.resultCode = HttpStatus.UNAUTHORIZED
        authInfo.response.setHeader("WWW-Authenticate", "Basic realm=\"Basic authenticaiton required\"")
        logError(authInfo, "Basic authentication failed, header 'authorization' not found.")
      } else if (log.isDebugEnabled) {
        logDebug(authInfo, "Basic authentication failed, no authentication given in header (OK).")
      }
      return
    }
    // Try basic authorization
    val basicAuthenticationData = BasicAuthenticationData(authInfo.request, authHeader)
    val username = basicAuthenticationData.username
    val secret = basicAuthenticationData.secret
    if (username == null || secret == null) {
      // Logging is done by BasicAuthenticationData.
      return
    }
    authInfo.userString = username // required for LoginProtection.incrementFailedLoginTimeOffset
    if (checkLoginProtection(authInfo, userTokenType)) {
      // Access denied (time offset due to failed logins). Logging is done by check method.
      return
    }
    authInfo.user = authenticate(username, secret)
    if (!authInfo.success) {
      logError(authInfo, "Basic authentication failed for user '$username'.")
    } else if (log.isDebugEnabled) {
      logDebug(authInfo, "Basic authentication was successful for user '$username'.")
    }
  }

  /**
   * Tries an authorization by token.
   * @param required If required, an error message is logged if no authentication is present. Otherwise only wrong credentials will result in error messages.
   * @param userTokenType Type of authentication (or null) for separating time penalties of login protection for different types (DAV, REST_CLIENT and normal login).
   */
  fun tokenAuthentication(
    authInfo: RestAuthenticationInfo,
    userTokenType: UserTokenType,
    required: Boolean
  ) {
    if (log.isDebugEnabled) {
      logDebug(authInfo, "Trying token based authentication...")
    }
    val authenticationToken = getAttribute(authInfo.request, *REQUEST_PARAMS_TOKEN)
    getUserString(authInfo, REQUEST_PARAMS_USER, userTokenType, required)
    val userId = authInfo.userString?.toIntOrNull()
    val username = if (userId == null) authInfo.userString else null
    tokenAuthentication(
      authInfo, userTokenType, authenticationToken, required,
      userParams = REQUEST_PARAMS_USER_ID,
      tokenParams = REQUEST_PARAMS_TOKEN,
      username = username,
      userId = userId
    )
  }

  /**
   * @param userTokenType Type of authentication (or null) for separating time penalties of login protection for different types (DAV, REST_CLIENT and normal login).
   * @param userParams Request parameter names to search for userId/username, only for logging purposes.
   * @param tokenParams Request parameter names to search for authentication token, only for logging purposes.
   */
  fun tokenAuthentication(
    authInfo: RestAuthenticationInfo,
    userTokenType: UserTokenType,
    authenticationToken: String?,
    required: Boolean,
    userParams: Array<String>,
    tokenParams: Array<String>,
    userId: Int? = null,
    username: String? = null
  ) {
    if (checkLoginProtection(authInfo, userTokenType)) {
      // Access denied (time offset due to failed logins). Logging is done by check method.
      return
    }
    if (authenticationToken.isNullOrBlank() || (userId == null && username.isNullOrBlank())) {
      if (authInfo.resultCode == null) {
        // error not yet handled.
        if (required) {
          logError(authInfo, "User not found (by request params ${joinToString(userParams)}) and/or authentication tokens (by request params ${
            joinToString(
              tokenParams
            )
          }). Rest call denied.")
          authInfo.resultCode = HttpStatus.BAD_REQUEST
        } else if (log.isDebugEnabled) {
          logDebug(
            authInfo,
            "User not found (by request params ${joinToString(userParams)}) and/or authentication tokens (by request params ${
              joinToString(tokenParams)
            }) (OK)."
          )
        }
      }
      return
    }
    authInfo.user = if (userId != null) {
      userAuthenticationsService.getUserByToken(authInfo.request, userId, userTokenType, authenticationToken)
    } else {
      userAuthenticationsService.getUserByToken(authInfo.request, username!!, userTokenType, authenticationToken)
    }
    if (authInfo.user == null) {
      logError(authInfo, "Bad request, user not found by username '$username' or id $userId and token.")
      authInfo.resultCode = HttpStatus.BAD_REQUEST
    } else {
      authInfo.loggedInByAuthenticationToken = true // Marking the user as logged in by authentication token.
      if (log.isDebugEnabled) {
        logDebug(authInfo, "User found by username '$username' or id $userId.")
      }
    }
  }

  /**
   * Re-usable doFilter method. Checks the system status and calls given authenticate method. In case of success, the
   * authenticated user will be put to the [ThreadLocalUserContext], calls the doFilter method and will be removed after request was finished.
   * @param request
   * @param response
   * @param userTokenType Type of authentication (or null) for separating time penalties of login protection for different types (DAV, REST_CLIENT and normal login).
   * @param authenticate The authenticate method tries to authenticate the user.
   * @param doFilter If authenticated, this method is called to proceed the request.
   */
  fun doFilter(
    request: ServletRequest,
    response: ServletResponse,
    userTokenType: UserTokenType?,
    authenticate: (authInfo: RestAuthenticationInfo) -> Unit,
    doFilter: () -> Unit
  ) {
    response as HttpServletResponse
    request as HttpServletRequest
    if (log.isDebugEnabled) {
      logDebug(request, "Processing request...")
    }
    if (!systemStatus.upAndRunning) {
      log.error("System isn't up and running, all rest calls are denied. The system is may-be in start-up phase or in maintenance mode.")
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
      return
    }
    if (UserFilter.isUpdateRequiredFirst()) {
      log.warn("Update of the system is required first. Login via Rest not available. Administrators login required.")
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
      return
    }
    val authInfo = RestAuthenticationInfo(request, response)
    authenticate(authInfo)
    if (!authInfo.success) {
      // Login failed, so increase time penalty for failed login for avoiding brute force attacks:
      if (!authInfo.lockedByTimePenalty) {
        // Increment only for login failures, but not increment again if login was denied due to a login penalty.
        LoginProtection.instance()
          .incrementFailedLoginTimeOffset(authInfo.userString, authInfo.clientIpAddress, userTokenType?.name)
      }
      response.sendError(authInfo.resultCode?.value() ?: HttpServletResponse.SC_UNAUTHORIZED)
      return
    }
    try {
      registerUser(request, authInfo, userTokenType)
      doFilter()
    } finally {
      unregister(request, response, authInfo)
    }
  }

  @JvmOverloads
  open fun getUserAccessLogEntries(tokenType: UserTokenType, userId: Int? = null): UserAccessLogEntries? {
    return userAuthenticationsService.getUserAccessLogEntries(tokenType, userId)
  }

  /**
   * You must use try { registerUser(...) } finally { unregisterUser() }!!!!
   *
   * @param request
   * @param authInfo
   * @param userTokenType Only needed for checking time penalty of login protection.
   */
  fun registerUser(request: HttpServletRequest, authInfo: RestAuthenticationInfo, userTokenType: UserTokenType?) {
    val user = authInfo.user!!
    val clientIpAddress = authInfo.clientIpAddress
    LoginProtection.instance().clearLoginTimeOffset(authInfo.userString, user.id, clientIpAddress, userTokenType?.name)
    var userContext = UserFilter.getUserContext(request)
    if (userContext != null) {
      userContext.user = user // Replace by fresh user from authentication.
      ThreadLocalUserContext.setUserContext(userContext)
    } else {
      userContext = ThreadLocalUserContext.setUser(user)
    }
    userContext.loggedInByAuthenticationToken = authInfo.loggedInByAuthenticationToken
    val settings = getConnectionSettings(request)
    ConnectionSettings.set(settings)
    val ip = request.getRemoteAddr()
    if (ip != null) {
      MDC.put("ip", ip)
    } else { // Only null in test case:
      MDC.put("ip", "unknown")
    }
    MDC.put("session", request.session?.id)
    MDC.put("user", user.username)
    MDC.put("userAgent", request.getHeader("User-Agent"))
    log.info("User: ${user.username} calls RestURL: ${request.requestURI} with ip: $clientIpAddress")
  }

  fun unregister(
    request: ServletRequest, response: ServletResponse,
    authInfo: RestAuthenticationInfo
  ) {
    ThreadLocalUserContext.setUser(null)
    ConnectionSettings.set(null)
    MDC.remove("ip")
    MDC.remove("user")
    MDC.remove("session")
    MDC.remove("userAgent")
    val resultCode = (response as HttpServletResponse).status
    if (resultCode != HttpStatus.OK.value() && resultCode != HttpStatus.MULTI_STATUS.value()) { // MULTI_STATUS (207) will be returned by milton.io (CalDAV/CardDAV), because XML is returned.
      val user = authInfo.user!!
      val clientIpAddress = authInfo.clientIpAddress
      log.error("User: ${user.username} calls RestURL: ${(request as HttpServletRequest).requestURI} with ip: $clientIpAddress: Response status not OK: status=${response.status}.")
    }
  }

  @Throws(IOException::class)
  private fun checkLoginProtection(authInfo: RestAuthenticationInfo, tokenType: UserTokenType?): Boolean {
    val offset = LoginProtection.instance()
      .getFailedLoginTimeOffsetIfExists(authInfo.userString, authInfo.clientIpAddress, tokenType?.name)
    if (offset > 0) {
      val seconds = (offset / 1000).toString()
      log.warn("The account for '${authInfo.userString}' is locked for $seconds seconds due to failed login attempts (ip=${authInfo.clientIpAddress}).")
      authInfo.resultCode = HttpStatus.FORBIDDEN
      authInfo.lockedByTimePenalty = true
      return true
    }
    return false
  }

  private fun getConnectionSettings(req: HttpServletRequest): ConnectionSettings {
    val settings = ConnectionSettings()
    val dateTimeFormatString = getAttribute(req, ConnectionSettings.DATE_TIME_FORMAT)
    if (dateTimeFormatString != null) {
      val dateTimeFormat = DateTimeFormat.valueOf(dateTimeFormatString.toUpperCase())
      settings.dateTimeFormat = dateTimeFormat
    }
    return settings
  }

  private fun logError(authInfo: RestAuthenticationInfo, msg: String) {
    log.error("$msg (requestUri=${RequestLog.asString(authInfo.request)})")
    SecurityLogging.logSecurityWarn(authInfo.request, this::class.java, "REST AUTHENTICATION FAILED", msg)
  }

  private fun logDebug(authInfo: RestAuthenticationInfo, msg: String) {
    logDebug(authInfo.request, msg)
  }

  private fun logDebug(request: HttpServletRequest, msg: String) {
    log.debug("$msg (request=${RequestLog.asString(request)})")
  }

  companion object {
    fun executeLogin(
      request: HttpServletRequest?,
      userContext: UserContext?
    ) { // Wicket part: (page.getSession() as MySession).login(userContext, page.getRequest())
      UserFilter.login(request, userContext)
    }

    /**
     * "Authentication-User-Id" and "authenticationUserId".
     */
    val REQUEST_PARAMS_USER_ID =
      arrayOf(Authentication.AUTHENTICATION_USER_ID, AuthenticationOld.AUTHENTICATION_USER_ID)

    /**
     * "Authentication-Token" and "authenticationToken".
     */
    val REQUEST_PARAMS_TOKEN = arrayOf(Authentication.AUTHENTICATION_TOKEN, AuthenticationOld.AUTHENTICATION_TOKEN)

    /**
     * "Authentication-Username" and "authenticationUsername".
     */
    val REQUEST_PARAMS_USERNAME =
      arrayOf(Authentication.AUTHENTICATION_USERNAME, AuthenticationOld.AUTHENTICATION_USERNAME)

    /**
     * "Authentication-User-Id", "Authentication-Username" and "authenticationUsername".
     */
    val REQUEST_PARAMS_USER = arrayOf(
      Authentication.AUTHENTICATION_USER_ID,
      Authentication.AUTHENTICATION_USERNAME,
      AuthenticationOld.AUTHENTICATION_USERNAME
    )

    fun joinToString(params: Array<String>): String {
      return params.joinToString(" or ", "'", "'") { it }
    }

    /**
     * Tries to get username or id from request parameters or header: Authentication-User-Id, Authentication-Username or
     * oder authenticationUsername.
     */
    fun getUserInfo(req: HttpServletRequest): String? {
      return getAttribute(req, *REQUEST_PARAMS_USER)
    }

    /**
     * @param req
     * @param keys Name of the parameter key. Additional keys may be given as alternative keys if first key isn't found.
     * Might be used for backwards compatibility.
     * @return
     */
    private fun getAttribute(req: HttpServletRequest, vararg keys: String): String? {
      keys.forEach { key ->
        var value = req.getHeader(key)
        if (value == null) {
          value = req.getParameter(key)
        }
        if (value != null) {
          return value
        }
      }
      return null
    }

    /**
     * @param req
     * @param keys Name of the header key. Additional keys may be given as alternative keys if first key isn't found.
     * Might be used for backwards compatibility.
     * @return
     */
    private fun getHeader(req: HttpServletRequest, vararg keys: String): String? {
      keys.forEach { key ->
        val value = req.getHeader(key)
        if (value != null) {
          return value
        }
      }
      return null
    }
  }
}
